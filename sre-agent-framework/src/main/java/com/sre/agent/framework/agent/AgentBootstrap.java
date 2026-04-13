package com.sre.agent.framework.agent;

import com.sre.agent.commons.model.AgentDefinition;
import com.sre.agent.framework.registry.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentBootstrap {

    private final AgentRegistry agentRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBuiltInAgents() {
        log.info("Registering built-in platform agents...");

        for (AgentDefinition agent : BuiltInAgents.all()) {
            // Try to load prompt from file, fall back to hardcoded
            String filePrompt = loadPromptFile("agent-" + agent.getAgentId() + ".md");
            if (filePrompt != null) {
                agent.setSystemPrompt(filePrompt);
                log.info("  ✓ {} — {} (prompt from file)", agent.getAgentId(), agent.getDisplayName());
            } else {
                log.info("  ✓ {} — {} (default prompt)", agent.getAgentId(), agent.getDisplayName());
            }
            agentRegistry.registerPlatformAgent(agent);
        }

        log.info("Built-in agent registration complete: {} agents registered", BuiltInAgents.all().size());
    }

    private String loadPromptFile(String filename) {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classpath:prompts/" + filename);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("No prompt file found for {}: {}", filename, e.getMessage());
        }
        return null;
    }
}
