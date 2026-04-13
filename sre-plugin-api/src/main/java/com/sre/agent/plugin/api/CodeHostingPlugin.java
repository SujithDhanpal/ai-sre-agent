package com.sre.agent.plugin.api;

import com.sre.agent.plugin.api.model.*;

import java.util.List;

public interface CodeHostingPlugin extends SrePlugin {

    String fetchFileContent(String repo, String path, String ref);

    List<String> listFiles(String repo, String path, String ref);

    List<CommitInfo> getRecentCommits(String repo, String branch, int limit);

    PullRequest createPullRequest(CreatePrRequest request);

    String getDiff(String repo, String baseBranch, String headBranch);

    List<String> searchCode(String repo, String query);

    List<DeploymentRecord> getRecentDeployments(String repo, int limit);

    String createBranch(String repo, String branchName, String fromRef);

    void commitFiles(String repo, String branch, String message, java.util.Map<String, String> files);
}
