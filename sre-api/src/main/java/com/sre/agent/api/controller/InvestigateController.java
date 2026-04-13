package com.sre.agent.api.controller;

import com.sre.agent.commons.enums.*;
import com.sre.agent.commons.model.*;
import com.sre.agent.core.repository.*;
import com.sre.agent.framework.tools.AgentToolBinder;
import com.sre.agent.framework.tools.SkillToolWrapper;
import com.sre.agent.framework.agent.BuiltInAgents;
import com.sre.agent.framework.registry.AgentRegistry;
import com.sre.agent.plugin.api.MonitoringPlugin;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.model.LogEntry;
import com.sre.agent.memory.service.MemoryService;
import com.sre.agent.memory.service.MemoryContext;
import com.sre.agent.skill.registry.SkillRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/investigate")
@RequiredArgsConstructor
@Slf4j
public class InvestigateController {

    private final ChatModel chatModel;
    private final PluginRegistry pluginRegistry;
    private final AgentToolBinder toolBinder;
    private final MemoryService memoryService;
    private final IncidentRepository incidentRepository;
    private final ConversationMessageRepository conversationRepo;
    private final InvestigationContextRepository contextRepo;
    private final AgentSkillToolRepository agentSkillToolRepo;
    private final SkillRegistryService skillRegistryService;
    private final com.sre.agent.core.context.ChangeCorrelation changeCorrelation;
    private final com.sre.agent.core.context.BlastRadius blastRadius;
    private final AgentRegistry agentRegistry;

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

    private String triagePrompt;
    private String followupPrompt;
    private String fixPrompt;
    private String dissatisfactionPrompt;
    private String specialistAssessmentPrompt;
    private String synthesisPrompt;

    @jakarta.annotation.PostConstruct
    private void loadPrompts() {
        triagePrompt = loadPromptFile("prompts/triage.md", "You are an SRE agent. Analyze logs and find root cause.");
        followupPrompt = loadPromptFile("prompts/followup.md", "You are an SRE agent. Answer using conversation history.");
        fixPrompt = loadPromptFile("prompts/fix.md", "You are an SRE agent. Generate the code fix.");
        dissatisfactionPrompt = loadPromptFile("prompts/dissatisfaction-check.md",
                "You are a sentiment classifier. Respond with ONLY 'yes' or 'no'.");
        specialistAssessmentPrompt = loadPromptFile("prompts/specialist-assessment.md", "");
        synthesisPrompt = loadPromptFile("prompts/synthesis.md", "");
        log.info("Loaded prompts: triage={}ch, followup={}ch, fix={}ch, dissatisfaction={}ch, specialist={}ch, synthesis={}ch",
                triagePrompt.length(), followupPrompt.length(), fixPrompt.length(),
                dissatisfactionPrompt.length(), specialistAssessmentPrompt.length(), synthesisPrompt.length());
    }

    private String loadPromptFile(String path, String fallback) {
        try {
            var resource = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResource("classpath:" + path);
            if (resource.exists()) return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not load prompt {}: {}", path, e.getMessage());
        }
        return fallback;
    }

    /**
     * Start a new investigation.
     * 1. Fast triage with single ReAct agent
     * 2. If complex, automatically dispatches specialist agents
     */
    @PostMapping
    public ResponseEntity<InvestigateResponse> investigate(@RequestBody InvestigateRequest request) {
        log.info("New investigation: {}", request.query());
        long start = System.currentTimeMillis();

        // Create incident
        Incident incident = Incident.builder()
                .tenantId("default")
                .correlationId(UUID.randomUUID().toString())
                .alertSource(AlertSource.CUSTOM_WEBHOOK)
                .severity(Severity.P2)
                .status(IncidentStatus.ANALYZING)
                .title(request.query())
                .description(request.query())
                .affectedServices(request.services() != null ? request.services() : List.of())
                .environment(request.environment() != null ? request.environment() : "production")
                .build();
        final Incident savedIncident = incidentRepository.save(incident);
        incident = savedIncident;
        UUID incidentId = savedIncident.getId();

        List<String> services = request.services() != null ? request.services() : List.of();

        // Gather all context in parallel — logs, changes, blast radius, memory
        var logsFuture = CompletableFuture.supplyAsync(() -> prefetchLogs(request));
        var changesFuture = CompletableFuture.supplyAsync(() -> changeCorrelation.check(services));
        var blastFuture = CompletableFuture.supplyAsync(() -> blastRadius.assess(services));
        var memoryFuture = CompletableFuture.supplyAsync(() -> {
            MemoryContext m = memoryService.gatherRelevantMemory("default", savedIncident);
            return m.hasRelevantMemory() ? m.toPromptContext() : "";
        });

        String logContext = logsFuture.join();
        String changeContext = changesFuture.join();
        String blastContext = blastFuture.join();
        String memoryContext = memoryFuture.join();

        // Combine all context into one block for the agent
        String fullContext = logContext
                + (changeContext.startsWith("No recent") ? "" : "\n\n## Recent Changes\n" + changeContext)
                + (blastContext.startsWith("No blast") ? "" : "\n\n## Blast Radius\n" + blastContext);

        // Persist context (fullContext includes logs + changes + blast radius)
        contextRepo.save(InvestigationContext.builder()
                .incidentId(incidentId)
                .logContext(fullContext)
                .memoryContext(memoryContext)
                .services(services)
                .build());

        // Save user message
        conversationRepo.save(ConversationMessage.builder()
                .incidentId(incidentId).role("user").content(request.query()).build());

        // Step 1: Fast triage with single ReAct agent (gets logs + changes + blast radius)
        String triageResult = callTriageAgent(incidentId, fullContext, memoryContext);

        // Step 2: Ask triage agent if specialists are needed
        List<String> neededSpecialists = assessSpecialistNeeds(triageResult, fullContext);

        String finalAnswer;
        if (neededSpecialists.isEmpty()) {
            finalAnswer = triageResult;
            log.info("Investigation {}: triage sufficient, no specialists needed", incidentId);
        } else {
            log.info("Investigation {}: dispatching specialists: {}", incidentId, neededSpecialists);
            String specialistFindings = dispatchSpecialists(neededSpecialists, fullContext, memoryContext, incident, triageResult);
            finalAnswer = synthesize(triageResult, specialistFindings);
        }

        // Save response
        conversationRepo.save(ConversationMessage.builder()
                .incidentId(incidentId).role("assistant").content(finalAnswer).build());

        incident.setResolutionSummary(finalAnswer);
        incident.setStatus(IncidentStatus.FIX_GENERATING);
        incidentRepository.save(incident);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Investigation {} complete in {}ms", incidentId, elapsed);

        return ResponseEntity.ok(new InvestigateResponse(incidentId.toString(), finalAnswer, elapsed));
    }

    /**
     * Follow-up question on an existing investigation.
     * If user expresses dissatisfaction, auto-escalates to specialist agents.
     */
    @PostMapping("/{incidentId}/ask")
    public ResponseEntity<InvestigateResponse> ask(
            @PathVariable UUID incidentId,
            @RequestBody AskRequest request) {

        log.info("Follow-up on {}: {}", incidentId, request.question());
        long start = System.currentTimeMillis();

        InvestigationContext ctx = contextRepo.findByIncidentId(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("No investigation found: " + incidentId));

        conversationRepo.save(ConversationMessage.builder()
                .incidentId(incidentId).role("user").content(request.question()).build());

        String answer;

        if (isDissatisfied(request.question())) {
            // User not satisfied → escalate to specialists
            log.info("Dissatisfaction detected on {} — escalating to specialists", incidentId);

            Incident incident = incidentRepository.findById(incidentId).orElse(null);

            // Get previous triage result from conversation
            List<ConversationMessage> history = conversationRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
            String previousDiagnosis = history.stream()
                    .filter(m -> "assistant".equals(m.getRole()))
                    .reduce((a, b) -> b) // last assistant message
                    .map(ConversationMessage::getContent)
                    .orElse("");

            // Ask which specialists to dispatch
            List<String> specialists = assessSpecialistNeeds(
                    previousDiagnosis + "\n\nUser feedback: " + request.question(),
                    ctx.getLogContext());

            if (specialists.isEmpty()) {
                // Force at least the core specialists
                specialists = List.of("log-analyst", "infra-agent", "code-analyst");
                log.info("No specific specialists identified — dispatching all core specialists");
            }

            log.info("Dispatching specialists for deeper investigation: {}", specialists);
            String specialistFindings = dispatchSpecialists(
                    specialists, ctx.getLogContext(), ctx.getMemoryContext(), incident, previousDiagnosis);
            answer = synthesize(previousDiagnosis + "\n\nUser was not satisfied: " + request.question(),
                    specialistFindings);

        } else {
            // Normal follow-up — single agent with conversation history
            answer = callWithHistory(incidentId, ctx.getLogContext(), ctx.getMemoryContext(), followupPrompt);
        }

        conversationRepo.save(ConversationMessage.builder()
                .incidentId(incidentId).role("assistant").content(answer).build());

        long elapsed = System.currentTimeMillis() - start;
        log.info("Follow-up on {} complete in {}ms", incidentId, elapsed);

        return ResponseEntity.ok(new InvestigateResponse(incidentId.toString(), answer, elapsed));
    }

    private boolean isDissatisfied(String question) {
        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system(dissatisfactionPrompt)
                    .user("User message: \"" + question + "\"")
                    .call().content();

            boolean dissatisfied = response != null && response.trim().toLowerCase().startsWith("yes");
            log.debug("Dissatisfaction check for '{}': {}", question, dissatisfied);
            return dissatisfied;
        } catch (Exception e) {
            log.warn("Dissatisfaction check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate fix for an existing investigation.
     */
    @PostMapping("/{incidentId}/fix")
    public ResponseEntity<InvestigateResponse> fix(@PathVariable UUID incidentId) {
        log.info("Fix requested for {}", incidentId);
        long start = System.currentTimeMillis();

        InvestigationContext ctx = contextRepo.findByIncidentId(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("No investigation found: " + incidentId));

        conversationRepo.save(ConversationMessage.builder()
                .incidentId(incidentId).role("user")
                .content("Generate the exact code fix as a unified diff patch. Read the actual source files and produce the minimal change needed.")
                .build());

        String answer = callWithHistory(incidentId, ctx.getLogContext(), ctx.getMemoryContext(), fixPrompt);

        conversationRepo.save(ConversationMessage.builder()
                .incidentId(incidentId).role("assistant").content(answer).build());

        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok(new InvestigateResponse(incidentId.toString(), answer, elapsed));
    }

    /**
     * Get conversation history.
     */
    @GetMapping("/{incidentId}/history")
    public ResponseEntity<List<ConversationMessage>> history(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(conversationRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId));
    }

    // ========== INTERNAL METHODS ==========

    /**
     * Step 1: Fast triage with single ReAct agent.
     */
    private String callTriageAgent(UUID incidentId, String logContext, String memoryContext) {
        var toolsWithDesc = buildTools();
        List<Object> tools = toolsWithDesc.tools();

        String systemPrompt = triagePrompt
                + "\n\n## Pre-Fetched Logs (REAL DATA)\n\n" + logContext
                + (memoryContext.isBlank() ? "" : "\n\n## Memory\n" + memoryContext)
                + toolsWithDesc.promptSection();

        // Include conversation history
        List<ConversationMessage> history = conversationRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        String userMessage = buildConversationPrompt(history);

        try {
            var promptSpec = ChatClient.builder(chatModel).build()
                    .prompt().system(systemPrompt).user(userMessage);
            if (!tools.isEmpty()) promptSpec = promptSpec.tools(tools.toArray());
            return promptSpec.call().content();
        } catch (Exception e) {
            log.error("Triage agent failed: {}", e.getMessage(), e);
            return "Triage failed: " + e.getMessage();
        }
    }

    /**
     * Step 2: Ask Claude which specialists are needed.
     * Returns list of agent IDs, or empty if no specialists needed.
     */
    private List<String> assessSpecialistNeeds(String triageResult, String logContext) {
        // Get all available agents (platform + custom)
        List<AgentDefinition> available = agentRegistry.getSpecialistAgents("default");
        if (available.isEmpty()) return List.of();

        String agentCatalog = available.stream()
                .map(a -> "- **" + a.getAgentId() + "**: " + a.getDescription())
                .collect(Collectors.joining("\n"));

        String prompt = specialistAssessmentPrompt
                .replace("{triageResult}", triageResult.substring(0, Math.min(triageResult.length(), 2000)))
                .replace("{agentCatalog}", agentCatalog);

        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system("You are a decision maker. Respond with ONLY agent IDs or NONE. No explanation.")
                    .user(prompt)
                    .call().content();

            if (response == null || response.trim().equalsIgnoreCase("NONE")) {
                return List.of();
            }

            // Parse agent IDs from response
            List<String> requested = Arrays.stream(response.split("[,\\n]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank() && !s.equalsIgnoreCase("NONE"))
                    .map(s -> s.replaceAll("[*\\-]", "").trim())
                    .filter(s -> available.stream().anyMatch(a -> a.getAgentId().equals(s)))
                    .collect(Collectors.toList());

            log.info("Specialist assessment: requested {}", requested);
            return requested;

        } catch (Exception e) {
            log.warn("Specialist assessment failed, proceeding without: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Step 3: Dispatch specialist agents in parallel.
     * All agents get the SAME pre-fetched logs — no re-querying.
     */
    private String dispatchSpecialists(List<String> agentIds, String logContext,
                                        String memoryContext, Incident incident,
                                        String triageContext) {
        List<CompletableFuture<String>> futures = agentIds.stream()
                .map(agentId -> CompletableFuture.supplyAsync(() -> {
                    log.info("Dispatching specialist: {}", agentId);
                    return runSpecialist(agentId, logContext, memoryContext, incident, triageContext);
                }))
                .collect(Collectors.toList());

        // Wait for all specialists
        List<String> findings = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        var sb = new StringBuilder();
        for (int i = 0; i < agentIds.size(); i++) {
            sb.append("### ").append(agentIds.get(i)).append("\n\n");
            sb.append(findings.get(i)).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    /**
     * Run a single specialist agent.
     */
    private String runSpecialist(String agentId, String logContext,
                                  String memoryContext, Incident incident,
                                  String triageContext) {
        // Find agent definition
        AgentDefinition agent = agentRegistry.getAgent("default", agentId).orElse(null);
        if (agent == null) {
            return "Agent not found: " + agentId;
        }

        // Build tools for this specific agent + skill-tools
        List<Object> tools = new ArrayList<>(toolBinder.bindToolsForAgent(agent, "default"));
        var skillDesc = new StringBuilder();
        for (var st : agentSkillToolRepo.findByAgentId(agentId)) {
            skillRegistryService.getSkill("default", st.getSkillId()).ifPresent(skill -> {
                tools.add(new SkillToolWrapper(skill.getSkillId(), skill.getDescription(),
                        skillRegistryService, "default"));
                skillDesc.append("- **").append(skill.getSkillId()).append("**: ")
                        .append(skill.getDescription()).append("\n");
            });
        }

        String systemPrompt = agent.getSystemPrompt()
                + "\n\n## Pre-Fetched Logs (REAL DATA — already gathered, do NOT re-query)\n\n" + logContext
                + (memoryContext.isBlank() ? "" : "\n\n## Memory\n" + memoryContext)
                + (skillDesc.isEmpty() ? "" : "\n\n## Available Skills\n" + skillDesc);

        String userPrompt = "Investigate this incident from your area of expertise.\n\n"
                + "**Title**: " + incident.getTitle() + "\n"
                + "**Services**: " + String.join(", ", incident.getAffectedServices()) + "\n\n"
                + (triageContext != null && !triageContext.isBlank()
                    ? "## Initial Triage (for context — do NOT just repeat this, investigate deeper from your angle)\n\n"
                      + triageContext + "\n\n"
                    : "")
                + "Analyze the pre-fetched logs above. Use your tools ONLY if you need additional information not in the logs.";

        try {
            var promptSpec = ChatClient.builder(chatModel).build()
                    .prompt().system(systemPrompt).user(userPrompt);
            if (!tools.isEmpty()) promptSpec = promptSpec.tools(tools.toArray());
            return promptSpec.call().content();
        } catch (Exception e) {
            log.error("Specialist {} failed: {}", agentId, e.getMessage());
            return "Specialist " + agentId + " failed: " + e.getMessage();
        }
    }

    /**
     * Step 4: Synthesize triage + specialist findings.
     */
    private String synthesize(String triageResult, String specialistFindings) {
        String prompt = synthesisPrompt
                .replace("{triageResult}", triageResult)
                .replace("{specialistFindings}", specialistFindings);

        try {
            return ChatClient.builder(chatModel).build()
                    .prompt()
                    .user(prompt)
                    .call().content();
        } catch (Exception e) {
            log.error("Synthesis failed: {}", e.getMessage());
            return "## Triage Result\n\n" + triageResult + "\n\n## Specialist Findings\n\n" + specialistFindings;
        }
    }

    /**
     * Call agent with full conversation history (for follow-ups).
     */
    private String callWithHistory(UUID incidentId, String logContext, String memoryContext, String basePrompt) {
        var toolsWithDesc = buildTools();
        List<Object> tools = toolsWithDesc.tools();

        String systemPrompt = basePrompt
                + "\n\n## Pre-Fetched Logs (REAL DATA)\n\n" + logContext
                + (memoryContext.isBlank() ? "" : "\n\n## Memory\n" + memoryContext)
                + toolsWithDesc.promptSection();

        List<ConversationMessage> history = conversationRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        String userMessage = buildConversationPrompt(history);

        try {
            var promptSpec = ChatClient.builder(chatModel).build()
                    .prompt().system(systemPrompt).user(userMessage);
            if (!tools.isEmpty()) promptSpec = promptSpec.tools(tools.toArray());
            return promptSpec.call().content();
        } catch (Exception e) {
            log.error("Agent call failed: {}", e.getMessage(), e);
            return "Investigation failed: " + e.getMessage();
        }
    }

    /**
     * Build tools: plugin tools + skill tools for the investigate agent.
     * Also returns skill descriptions for injection into the system prompt.
     */
    private ToolsWithDescriptions buildTools() {
        var codeAnalyst = BuiltInAgents.codeAnalyst();
        List<Object> tools = new ArrayList<>(toolBinder.bindToolsForAgent(codeAnalyst, "default"));

        // Add skill-tools and collect their descriptions
        var skillDescriptions = new StringBuilder();
        for (var st : agentSkillToolRepo.findByAgentId("investigate")) {
            skillRegistryService.getSkill("default", st.getSkillId()).ifPresent(skill -> {
                tools.add(new SkillToolWrapper(skill.getSkillId(), skill.getDescription(),
                        skillRegistryService, "default"));
                skillDescriptions.append("- **").append(skill.getSkillId()).append("**: ")
                        .append(skill.getDescription())
                        .append(". Call via the `execute` tool with parameters as key=value pairs.\n");
                log.debug("Bound skill '{}' as tool for agent 'investigate'", skill.getSkillId());
            });
        }

        return new ToolsWithDescriptions(tools, skillDescriptions.toString());
    }

    private record ToolsWithDescriptions(List<Object> tools, String skillDescriptions) {
        String promptSection() {
            if (skillDescriptions.isBlank()) return "";
            return "\n\n## Available Skills (call via the `execute` tool)\n"
                    + "You have these skills available. To use them, call `execute` with the right parameters.\n\n"
                    + skillDescriptions;
        }
    }

    private String buildConversationPrompt(List<ConversationMessage> history) {
        var sb = new StringBuilder();
        for (ConversationMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                sb.append("**User**: ").append(msg.getContent()).append("\n\n");
            } else {
                sb.append("**Your previous answer**: ").append(msg.getContent()).append("\n\n");
            }
        }
        sb.append("Answer the latest user question. Use pre-fetched logs and previous answers. Only use tools if you need NEW information.");
        return sb.toString();
    }

    private String prefetchLogs(InvestigateRequest request) {
        return pluginRegistry.getPlugin("default", PluginType.MONITORING, MonitoringPlugin.class)
                .map(monitoring -> {
                    var sb = new StringBuilder();
                    List<String> services = request.services() != null ? request.services() : List.of();
                    Instant end = Instant.now();
                    Instant start7d = end.minusSeconds(604800);

                    Matcher matcher = UUID_PATTERN.matcher(request.query());
                    List<String> ids = new ArrayList<>();
                    while (matcher.find()) ids.add(matcher.group());

                    for (String id : ids) {
                        for (String service : services) {
                            String query = "{service_name=\"" + service + "\"} |= \"" + id + "\"";
                            log.info("Pre-fetch: {} in {}", id, service);
                            try {
                                List<LogEntry> logs = monitoring.fetchLogsRaw(query, start7d, end, 30);
                                if (!logs.isEmpty()) {
                                    sb.append("### Logs for ").append(id).append(" in ").append(service)
                                            .append(" (").append(logs.size()).append(" entries)\n\n");
                                    for (LogEntry l : logs) {
                                        sb.append("[").append(l.timestamp()).append("] ")
                                                .append(l.level()).append(" ").append(l.message()).append("\n");
                                    }
                                    sb.append("\n");
                                }
                            } catch (Exception e) {
                                log.warn("Pre-fetch failed: {}", e.getMessage());
                            }
                        }
                    }

                    if (sb.isEmpty() && !services.isEmpty()) {
                        for (String service : services) {
                            String query = "{service_name=\"" + service + "\"} |~ \"ERROR|Exception\"";
                            try {
                                List<LogEntry> logs = monitoring.fetchLogsRaw(query, end.minusSeconds(3600), end, 20);
                                if (!logs.isEmpty()) {
                                    sb.append("### Recent errors in ").append(service).append("\n\n");
                                    for (LogEntry l : logs) {
                                        sb.append("[").append(l.timestamp()).append("] ")
                                                .append(l.level()).append(" ").append(l.message()).append("\n");
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Error fetch failed: {}", e.getMessage());
                            }
                        }
                    }

                    return sb.isEmpty() ? "No logs found." : sb.toString();
                })
                .orElse("Monitoring plugin not available.");
    }

    public record InvestigateRequest(String query, List<String> services, String environment) {}
    public record AskRequest(String question) {}
    public record InvestigateResponse(String incidentId, String answer, long timeMs) {}
}
