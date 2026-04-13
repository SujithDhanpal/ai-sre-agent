package com.sre.agent.framework.tools;

import com.sre.agent.commons.InvestigationEventStream;
import com.sre.agent.skill.engine.SkillEngine;
import com.sre.agent.skill.registry.SkillRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps a registered skill as a @Tool so the LLM can call it during investigation.
 * One instance per skill-agent binding.
 */
@Slf4j
public class SkillToolWrapper {

    private final String skillId;
    private final String description;
    private final SkillRegistryService skillRegistry;
    private final String tenantId;

    public SkillToolWrapper(String skillId, String description,
                            SkillRegistryService skillRegistry, String tenantId) {
        this.skillId = skillId;
        this.description = description;
        this.skillRegistry = skillRegistry;
        this.tenantId = tenantId;
    }

    @Tool(description = "Execute a skill to query external systems. Pass parameters as key=value pairs separated by semicolons.")
    public String execute(
            @ToolParam(description = "Parameters as key=value pairs separated by semicolons. Example: 'database=esd;sql_query=SELECT COUNT(*) FROM users WHERE tenant_id = 17'") String params) {

        InvestigationEventStream.toolCall("skill:" + skillId, params != null ? params : "");
        log.info("[SkillTool:{}] Executing with params: {}", skillId, params);

        Map<String, String> paramMap = new HashMap<>();
        if (params != null && !params.isBlank()) {
            for (String pair : params.split(";")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    paramMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        try {
            var result = skillRegistry.executeSkill(tenantId, skillId, paramMap, null);
            log.info("[SkillTool:{}] Completed: passed={}, time={}ms", skillId, result.allPassed(), result.totalExecutionTimeMs());
            return result.summary();
        } catch (Exception e) {
            log.error("[SkillTool:{}] Failed: {}", skillId, e.getMessage());
            return "Skill '" + skillId + "' failed: " + e.getMessage();
        }
    }
}
