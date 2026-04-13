package com.sre.agent.api.controller;

import com.sre.agent.commons.InvestigationEventStream;
import com.sre.agent.commons.enums.*;
import com.sre.agent.commons.model.*;
import com.sre.agent.core.context.BlastRadius;
import com.sre.agent.core.context.ChangeCorrelation;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/investigate")
@RequiredArgsConstructor
@Slf4j
public class InvestigateStreamController {

    private final ChatModel chatModel;
    private final PluginRegistry pluginRegistry;
    private final AgentToolBinder toolBinder;
    private final MemoryService memoryService;
    private final IncidentRepository incidentRepository;
    private final ConversationMessageRepository conversationRepo;
    private final InvestigationContextRepository contextRepo;
    private final AgentSkillToolRepository agentSkillToolRepo;
    private final SkillRegistryService skillRegistryService;
    private final ChangeCorrelation changeCorrelation;
    private final BlastRadius blastRadius;

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

    private String triagePrompt;
    private String followupPrompt;
    private String fixPrompt;

    @jakarta.annotation.PostConstruct
    private void loadPrompts() {
        triagePrompt = loadPromptFile("prompts/triage.md", "You are an SRE agent. Analyze logs and find root cause.");
        followupPrompt = loadPromptFile("prompts/followup.md", "You are an SRE agent. Answer using conversation history.");
        fixPrompt = loadPromptFile("prompts/fix.md", "You are an SRE agent. Generate the code fix.");
        log.info("Loaded streaming prompts: triage={}ch, followup={}ch, fix={}ch",
                triagePrompt.length(), followupPrompt.length(), fixPrompt.length());
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

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter investigateStream(@RequestBody InvestigateController.InvestigateRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
        log.info("Streaming investigation: {}", request.query());

        CompletableFuture.runAsync(() -> {
            try {
                // Wire the event stream so tools can emit events
                InvestigationEventStream.set((event, data) -> {
                    try {
                        emitter.send(SseEmitter.event().name(event).data(data));
                    } catch (IOException e) {
                        log.debug("SSE send failed: {}", e.getMessage());
                    }
                });

                InvestigationEventStream.status("Creating investigation...");

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
                final Incident saved = incidentRepository.save(incident);
                UUID incidentId = saved.getId();
                List<String> services = request.services() != null ? request.services() : List.of();

                // Context gathering with events
                InvestigationEventStream.status("Pre-fetching logs from Loki...");
                String logContext = prefetchLogs(request);

                InvestigationEventStream.status("Checking recent deploys...");
                String changeContext = changeCorrelation.check(services);

                InvestigationEventStream.status("Assessing blast radius...");
                String blastContext = blastRadius.assess(services);

                InvestigationEventStream.status("Loading memory...");
                MemoryContext memory = memoryService.gatherRelevantMemory("default", saved);
                String memoryContext = memory.hasRelevantMemory() ? memory.toPromptContext() : "";

                String fullContext = logContext
                        + (changeContext.startsWith("No recent") ? "" : "\n\n## Recent Changes\n" + changeContext)
                        + (blastContext.startsWith("No blast") ? "" : "\n\n## Blast Radius\n" + blastContext);

                // Persist context
                contextRepo.save(InvestigationContext.builder()
                        .incidentId(incidentId).logContext(fullContext)
                        .memoryContext(memoryContext).services(services).build());

                conversationRepo.save(ConversationMessage.builder()
                        .incidentId(incidentId).role("user").content(request.query()).build());

                // Triage agent
                InvestigationEventStream.status("Starting triage agent with " + countTools() + " tools...");
                String answer = callAgent(incidentId, fullContext, memoryContext, triagePrompt);

                conversationRepo.save(ConversationMessage.builder()
                        .incidentId(incidentId).role("assistant").content(answer).build());

                saved.setResolutionSummary(answer);
                saved.setStatus(IncidentStatus.FIX_GENERATING);
                incidentRepository.save(saved);

                InvestigationEventStream.answer(answer);
                InvestigationEventStream.done("{\"incidentId\":\"" + incidentId + "\"}");

                emitter.complete();

            } catch (Exception e) {
                log.error("Streaming investigation failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                InvestigationEventStream.clear();
            }
        });

        return emitter;
    }

    @PostMapping(value = "/{incidentId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@PathVariable UUID incidentId,
                                 @RequestBody InvestigateController.AskRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L);
        log.info("Streaming follow-up on {}: {}", incidentId, request.question());

        CompletableFuture.runAsync(() -> {
            try {
                InvestigationEventStream.set((event, data) -> {
                    try {
                        emitter.send(SseEmitter.event().name(event).data(data));
                    } catch (IOException e) {
                        log.debug("SSE send failed: {}", e.getMessage());
                    }
                });

                InvestigationContext ctx = contextRepo.findByIncidentId(incidentId)
                        .orElseThrow(() -> new IllegalArgumentException("No investigation found: " + incidentId));

                conversationRepo.save(ConversationMessage.builder()
                        .incidentId(incidentId).role("user").content(request.question()).build());

                InvestigationEventStream.status("Processing follow-up...");
                String answer = callAgent(incidentId, ctx.getLogContext(), ctx.getMemoryContext(), followupPrompt);

                conversationRepo.save(ConversationMessage.builder()
                        .incidentId(incidentId).role("assistant").content(answer).build());

                InvestigationEventStream.answer(answer);
                InvestigationEventStream.done("{\"incidentId\":\"" + incidentId + "\"}");

                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming follow-up failed: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            } finally {
                InvestigationEventStream.clear();
            }
        });

        return emitter;
    }

    @PostMapping(value = "/{incidentId}/fix/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fixStream(@PathVariable UUID incidentId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        log.info("Streaming fix for {}", incidentId);

        CompletableFuture.runAsync(() -> {
            try {
                InvestigationEventStream.set((event, data) -> {
                    try {
                        emitter.send(SseEmitter.event().name(event).data(data));
                    } catch (IOException e) {
                        log.debug("SSE send failed: {}", e.getMessage());
                    }
                });

                InvestigationContext ctx = contextRepo.findByIncidentId(incidentId)
                        .orElseThrow(() -> new IllegalArgumentException("No investigation found: " + incidentId));

                String fixQ = "Generate the exact code fix as a unified diff patch. Read the actual source files and produce the minimal change needed.";
                conversationRepo.save(ConversationMessage.builder()
                        .incidentId(incidentId).role("user").content(fixQ).build());

                InvestigationEventStream.status("Generating fix...");
                String answer = callAgent(incidentId, ctx.getLogContext(), ctx.getMemoryContext(), fixPrompt);

                conversationRepo.save(ConversationMessage.builder()
                        .incidentId(incidentId).role("assistant").content(answer).build());

                InvestigationEventStream.answer(answer);
                InvestigationEventStream.done("{\"incidentId\":\"" + incidentId + "\"}");

                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming fix failed: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            } finally {
                InvestigationEventStream.clear();
            }
        });

        return emitter;
    }

    private String callAgent(UUID incidentId, String logContext, String memoryContext, String basePrompt) {
        var codeAnalyst = BuiltInAgents.codeAnalyst();
        List<Object> tools = new ArrayList<>(toolBinder.bindToolsForAgent(codeAnalyst, "default"));

        var skillDesc = new StringBuilder();
        for (var st : agentSkillToolRepo.findByAgentId("investigate")) {
            skillRegistryService.getSkill("default", st.getSkillId()).ifPresent(skill -> {
                tools.add(new SkillToolWrapper(skill.getSkillId(), skill.getDescription(),
                        skillRegistryService, "default"));
                skillDesc.append("- **").append(skill.getSkillId()).append("**: ")
                        .append(skill.getDescription()).append("\n");
            });
        }

        String systemPrompt = basePrompt
                + "\n\n## Pre-Fetched Logs (REAL DATA)\n\n" + logContext
                + (memoryContext.isBlank() ? "" : "\n\n## Memory\n" + memoryContext)
                + (skillDesc.isEmpty() ? "" : "\n\n## Available Skills\n" + skillDesc);

        List<ConversationMessage> history = conversationRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        var sb = new StringBuilder();
        for (ConversationMessage msg : history) {
            if ("user".equals(msg.getRole())) sb.append("**User**: ").append(msg.getContent()).append("\n\n");
            else sb.append("**Your previous answer**: ").append(msg.getContent()).append("\n\n");
        }
        sb.append("Answer the latest user question.");

        try {
            var promptSpec = ChatClient.builder(chatModel).build()
                    .prompt().system(systemPrompt).user(sb.toString());
            if (!tools.isEmpty()) promptSpec = promptSpec.tools(tools.toArray());
            return promptSpec.call().content();
        } catch (Exception e) {
            log.error("Agent call failed: {}", e.getMessage(), e);
            return "Investigation failed: " + e.getMessage();
        }
    }

    private int countTools() {
        return toolBinder.bindToolsForAgent(BuiltInAgents.codeAnalyst(), "default").size();
    }

    private String prefetchLogs(InvestigateController.InvestigateRequest request) {
        return pluginRegistry.getPlugin("default", com.sre.agent.commons.enums.PluginType.MONITORING, MonitoringPlugin.class)
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
                            try {
                                List<LogEntry> logs = monitoring.fetchLogsRaw(
                                        "{service_name=\"" + service + "\"} |= \"" + id + "\"",
                                        start7d, end, 30);
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
                    return sb.isEmpty() ? "No logs found." : sb.toString();
                })
                .orElse("Monitoring plugin not available.");
    }
}
