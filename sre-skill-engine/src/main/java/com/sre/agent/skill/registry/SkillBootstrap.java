package com.sre.agent.skill.registry;

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
public class SkillBootstrap {

    private final SkillRegistryService skillRegistryService;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBuiltInSkills() {
        log.info("Registering built-in platform skills...");

        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/platform/*.md");

            int count = 0;
            for (Resource resource : resources) {
                try {
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    var skill = skillRegistryService.registerPlatformSkill(content);
                    log.info("  ✓ {} — {}", skill.getSkillId(), skill.getDisplayName());
                    count++;
                } catch (Exception e) {
                    log.warn("  ✗ Failed to register {}: {}", resource.getFilename(), e.getMessage());
                }
            }

            log.info("Built-in skill registration complete: {} skills registered", count);

        } catch (Exception e) {
            log.warn("No built-in skills found: {}", e.getMessage());
        }
    }
}
