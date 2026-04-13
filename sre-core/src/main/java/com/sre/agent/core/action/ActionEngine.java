package com.sre.agent.core.action;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.commons.model.Incident;
import com.sre.agent.plugin.api.CodeHostingPlugin;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.model.CreatePrRequest;
import com.sre.agent.plugin.api.model.PullRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionEngine {

    private final PluginRegistry pluginRegistry;

    public String createPullRequest(Incident incident, String fix, String repo) {
        return pluginRegistry.getPlugin("default", PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    try {
                        String branchName = "sre-agent/fix-" + incident.getId().toString().substring(0, 8);
                        github.createBranch(repo, branchName, "main");
                        github.commitFiles(repo, branchName,
                                "[SRE-Agent] Fix: " + incident.getTitle(),
                                Map.of("fix.patch", fix));

                        PullRequest pr = github.createPullRequest(new CreatePrRequest(
                                repo,
                                "[SRE-Agent] " + incident.getTitle(),
                                "## SRE Agent Auto-Fix\n\n" + incident.getResolutionSummary(),
                                branchName, "main",
                                List.of("sre-agent"), Map.of()));

                        if (pr != null) {
                            log.info("PR created: {}", pr.url());
                            return pr.url();
                        }
                        return "PR creation failed — no response from GitHub";
                    } catch (Exception e) {
                        log.error("PR creation failed: {}", e.getMessage());
                        return "PR creation failed: " + e.getMessage();
                    }
                })
                .orElse("GitHub plugin not available.");
    }
}
