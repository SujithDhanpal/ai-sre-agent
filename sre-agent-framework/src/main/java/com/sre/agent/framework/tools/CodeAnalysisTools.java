package com.sre.agent.framework.tools;
import com.sre.agent.commons.InvestigationEventStream;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.CodeHostingPlugin;
import com.sre.agent.plugin.api.ErrorTrackingPlugin;
import com.sre.agent.plugin.api.PluginRegistry;
import com.sre.agent.plugin.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class CodeAnalysisTools {

    private final PluginRegistry pluginRegistry;
    private final String tenantId;

    @Tool(description = "Read a source code file from a GitHub repository. Use this to examine the code at a specific file path to understand what it does and find bugs.")
    public String readSourceFile(
            @ToolParam(description = "Repository name, e.g. 'payment-service'") String repo,
            @ToolParam(description = "File path within the repo, e.g. 'src/main/java/com/example/PaymentProcessor.java'") String filePath) {

        InvestigationEventStream.toolCall("readSourceFile", repo + "/" + filePath);
        log.info("[Tool] readSourceFile: repo={}, path={}", repo, filePath);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    String content = github.fetchFileContent(repo, filePath, "main");
                    if (content == null) return "File not found: " + repo + "/" + filePath;
                    if (content.length() > 10000) {
                        return content.substring(0, 10000) + "\n... [truncated, file is " + content.length() + " chars]";
                    }
                    return content;
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Search for code patterns across a repository. Use this to find where a class, method, or variable is used, or to find similar code patterns.")
    public String searchCode(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "Search query — class name, method name, error message, etc.") String query) {

        InvestigationEventStream.toolCall("searchCode", repo + ": " + query);
        log.info("[Tool] searchCode: repo={}, query={}", repo, query);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    List<String> results = github.searchCode(repo, query);
                    if (results.isEmpty()) return "No code matches found for: " + query;

                    return "Files matching '%s':\n%s".formatted(query,
                            results.stream().map(f -> "- " + f).collect(Collectors.joining("\n")));
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Get recent git commits for a repository on a given branch. Use this to see what code changes happened recently, especially around the time of the incident.")
    public String getRecentCommits(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "Branch name, e.g. 'main'") String branch,
            @ToolParam(description = "Number of commits to fetch, e.g. 10") int limit) {

        InvestigationEventStream.toolCall("getRecentCommits", repo + "/" + branch);
        log.info("[Tool] getRecentCommits: repo={}, branch={}, limit={}", repo, branch, limit);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    List<CommitInfo> commits = github.getRecentCommits(repo, branch, Math.min(limit, 20));
                    if (commits.isEmpty()) return "No recent commits found.";

                    return commits.stream()
                            .map(c -> "- [%s] %s by %s (%s)".formatted(
                                    c.sha().substring(0, 7), c.message().split("\n")[0],
                                    c.author(), c.timestamp()))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Get the diff between two branches or commits. Use this to see exactly what code changed in a deployment.")
    public String getDiff(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "Base branch or commit SHA") String base,
            @ToolParam(description = "Head branch or commit SHA") String head) {

        InvestigationEventStream.toolCall("getDiff", repo + ": " + base + "..." + head);
        log.info("[Tool] getDiff: repo={}, base={}, head={}", repo, base, head);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    String diff = github.getDiff(repo, base, head);
                    if (diff == null || diff.isBlank()) return "No diff found between " + base + " and " + head;
                    if (diff.length() > 15000) {
                        return diff.substring(0, 15000) + "\n... [truncated, diff is " + diff.length() + " chars]";
                    }
                    return diff;
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Get recent deployments for a repository. Use this to check if a deployment correlates with the incident start time.")
    public String getRecentDeployments(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "Number of deployments to fetch") int limit) {

        InvestigationEventStream.toolCall("getRecentDeployments", repo);
        log.info("[Tool] getRecentDeployments: repo={}, limit={}", repo, limit);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    List<DeploymentRecord> deploys = github.getRecentDeployments(repo, Math.min(limit, 10));
                    if (deploys.isEmpty()) return "No recent deployments found.";

                    return deploys.stream()
                            .map(d -> "- [%s] %s → %s by %s (%s)".formatted(
                                    d.deploymentId(), d.version(), d.status(),
                                    d.deployedBy(), d.deployedAt()))
                            .collect(Collectors.joining("\n"));
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Check release health in Sentry: crash-free rate, new errors introduced by a release. Use this to determine if a specific deploy introduced regressions.")
    public String checkReleaseHealth(
            @ToolParam(description = "Service/project name in Sentry") String service,
            @ToolParam(description = "Release version, e.g. 'v2.4.1'") String version) {

        InvestigationEventStream.toolCall("checkReleaseHealth", service + " " + version);
        log.info("[Tool] checkReleaseHealth: service={}, version={}", service, version);

        return pluginRegistry.getPlugin(tenantId, PluginType.ERROR_TRACKING, ErrorTrackingPlugin.class)
                .map(sentry -> {
                    ReleaseHealth health = sentry.getReleaseHealth(service, version);
                    if (health == null) return "Release not found: " + version;

                    return "Release: %s\nCrash-free sessions: %.1f%%\nCrash-free users: %.1f%%\nTotal sessions: %d\nErrors: %d\nNew issues: %d\nDeployed: %s".formatted(
                            health.version(), health.crashFreeSessionsPercent(),
                            health.crashFreeUsersPercent(), health.totalSessions(),
                            health.errorCount(), health.newIssues(), health.deployedAt());
                })
                .orElse("Error tracking plugin not available.");
    }

    @Tool(description = "Create a new git branch from main (or another ref). Use this as the first step when creating a pull request. If the branch already exists, you'll get an error — just use the existing branch name.")
    public String createBranch(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "New branch name, e.g. 'sre-fix/lazy-loading-fix'") String branchName,
            @ToolParam(description = "Source branch to create from, usually 'main'") String fromRef) {

        InvestigationEventStream.toolCall("createBranch", repo + ": " + branchName);
        log.info("[Tool] createBranch: repo={}, branch={}, from={}", repo, branchName, fromRef);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    String result = github.createBranch(repo, branchName, fromRef);
                    if (result != null) return "Branch created: " + result;
                    return "Failed to create branch. It may already exist — try using it directly.";
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Commit a file change to a branch. Use this after creating a branch to push your code fix. The content should be the FULL new file content, not a diff.")
    public String commitFile(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "Branch name to commit to") String branch,
            @ToolParam(description = "File path in the repo, e.g. 'src/main/java/com/example/MyService.java'") String filePath,
            @ToolParam(description = "The full new content of the file") String fileContent,
            @ToolParam(description = "Commit message") String commitMessage) {

        InvestigationEventStream.toolCall("commitFile", repo + ": " + filePath);
        log.info("[Tool] commitFile: repo={}, branch={}, path={}", repo, branch, filePath);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    try {
                        github.commitFiles(repo, branch, commitMessage, java.util.Map.of(filePath, fileContent));
                        return "File committed: " + filePath + " on branch " + branch;
                    } catch (Exception e) {
                        return "Commit failed: " + e.getMessage();
                    }
                })
                .orElse("Code hosting plugin not available.");
    }

    @Tool(description = "Create a pull request on GitHub. Use this after creating a branch and committing your fix. The branch must have at least one commit different from the base branch.")
    public String createPullRequest(
            @ToolParam(description = "Repository name") String repo,
            @ToolParam(description = "PR title") String title,
            @ToolParam(description = "PR description/body with details about the fix") String body,
            @ToolParam(description = "Head branch (your fix branch)") String headBranch,
            @ToolParam(description = "Base branch, usually 'main'") String baseBranch) {

        InvestigationEventStream.toolCall("createPullRequest", repo + ": " + title);
        log.info("[Tool] createPullRequest: repo={}, title={}, head={}, base={}", repo, title, headBranch, baseBranch);

        return pluginRegistry.getPlugin(tenantId, PluginType.CODE_HOSTING, CodeHostingPlugin.class)
                .map(github -> {
                    var pr = github.createPullRequest(new com.sre.agent.plugin.api.model.CreatePrRequest(
                            repo, title, body, headBranch, baseBranch,
                            java.util.List.of("sre-agent"), java.util.Map.of()));
                    if (pr != null && pr.url() != null) {
                        return "Pull request created: " + pr.url();
                    }
                    return "PR creation failed. Make sure the branch has commits different from " + baseBranch + ".";
                })
                .orElse("Code hosting plugin not available.");
    }
}
