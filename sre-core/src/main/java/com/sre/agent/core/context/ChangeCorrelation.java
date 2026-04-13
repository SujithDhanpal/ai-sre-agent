package com.sre.agent.core.context;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.CodeHostingPlugin;
import com.sre.agent.plugin.api.InfrastructurePlugin;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.model.CommitInfo;
import com.sre.agent.plugin.api.model.DeploymentRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks if there were recent deploys or code changes correlated with the incident.
 * Not an LLM agent — just quick API calls to GitHub/AWS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeCorrelation {

    private final PluginRegistry pluginRegistry;

    public String check(List<String> services) {
        var sb = new StringBuilder();

        pluginRegistry.getPlugin("default", PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .ifPresent(github -> {
                    for (String service : services) {
                        try {
                            List<DeploymentRecord> deploys = github.getRecentDeployments(service, 5);
                            if (!deploys.isEmpty()) {
                                sb.append("### Recent deployments for ").append(service).append("\n");
                                for (var d : deploys) {
                                    sb.append("- ").append(d.version())
                                            .append(" by ").append(d.deployedBy())
                                            .append(" at ").append(d.deployedAt())
                                            .append(" (").append(d.status()).append(")\n");
                                }
                                sb.append("\n");
                            }

                            List<CommitInfo> commits = github.getRecentCommits(service, "main", 5);
                            if (!commits.isEmpty()) {
                                sb.append("### Recent commits for ").append(service).append("\n");
                                for (var c : commits) {
                                    sb.append("- [").append(c.sha().substring(0, 7)).append("] ")
                                            .append(c.message().split("\n")[0])
                                            .append(" by ").append(c.author())
                                            .append(" at ").append(c.timestamp()).append("\n");
                                }
                                sb.append("\n");
                            }
                        } catch (Exception e) {
                            log.warn("Change correlation failed for {}: {}", service, e.getMessage());
                        }
                    }
                });

        return sb.isEmpty() ? "No recent changes found." : sb.toString();
    }
}
