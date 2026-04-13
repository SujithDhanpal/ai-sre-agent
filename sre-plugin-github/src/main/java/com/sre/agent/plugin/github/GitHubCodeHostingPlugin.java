package com.sre.agent.plugin.github;

import com.sre.agent.commons.enums.PluginType;
import com.sre.agent.plugin.api.CodeHostingPlugin;
import com.sre.agent.plugin.api.PluginConfiguration;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigField;
import com.sre.agent.plugin.api.PluginConfiguration.ConfigFieldType;
import com.sre.agent.plugin.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "sre.plugins.github.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class GitHubCodeHostingPlugin implements CodeHostingPlugin {

    private final WebClient githubClient;
    private final GitHubPluginProperties properties;

    public GitHubCodeHostingPlugin(WebClient.Builder webClientBuilder, GitHubPluginProperties properties) {
        this.properties = properties;
        this.githubClient = webClientBuilder.clone()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (properties.getToken() != null ? properties.getToken() : ""))
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public String fetchFileContent(String repo, String path, String ref) {
        log.debug("Fetching file content: repo={}, path={}, ref={}", repo, path, ref);
        try {
            Map<String, Object> response = githubClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                            properties.getDefaultOwner(), repo, path, ref != null ? ref : "main")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) return null;
            String content = (String) response.get("content");
            if (content != null) {
                return new String(Base64.getMimeDecoder().decode(content.replaceAll("\\s", "")));
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch file from GitHub: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> listFiles(String repo, String path, String ref) {
        log.debug("Listing files: repo={}, path={}", repo, path);
        try {
            List<Map<String, Object>> response = githubClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                            properties.getDefaultOwner(), repo, path != null ? path : "", ref != null ? ref : "main")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (response == null) return List.of();
            return response.stream()
                    .map(f -> (String) f.get("path"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list files from GitHub: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CommitInfo> getRecentCommits(String repo, String branch, int limit) {
        log.debug("Fetching recent commits: repo={}, branch={}, limit={}", repo, branch, limit);
        try {
            List<Map<String, Object>> response = githubClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/commits")
                            .queryParam("sha", branch != null ? branch : "main")
                            .queryParam("per_page", Math.min(limit, 100))
                            .build(properties.getDefaultOwner(), repo))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (response == null) return List.of();
            return response.stream()
                    .map(c -> {
                        Map<String, Object> commit = (Map<String, Object>) c.get("commit");
                        Map<String, Object> author = (Map<String, Object>) commit.get("author");
                        return new CommitInfo(
                                (String) c.get("sha"),
                                (String) commit.get("message"),
                                (String) author.get("name"),
                                Instant.parse((String) author.get("date")),
                                List.of()
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch commits from GitHub: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public PullRequest createPullRequest(CreatePrRequest request) {
        log.info("Creating PR: repo={}, title={}", request.repo(), request.title());
        try {
            Map<String, Object> body = Map.of(
                    "title", request.title(),
                    "body", request.body(),
                    "head", request.headBranch(),
                    "base", request.baseBranch() != null ? request.baseBranch() : "main"
            );

            Map<String, Object> response = githubClient.post()
                    .uri("/repos/{owner}/{repo}/pulls", properties.getDefaultOwner(), request.repo())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) return null;
            return new PullRequest(
                    ((Number) response.get("number")).longValue(),
                    (String) response.get("html_url"),
                    (String) response.get("title"),
                    (String) response.get("body"),
                    (String) response.get("state"),
                    request.headBranch(),
                    request.baseBranch(),
                    request.labels()
            );
        } catch (Exception e) {
            log.error("Failed to create PR on GitHub: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getDiff(String repo, String baseBranch, String headBranch) {
        log.debug("Fetching diff: repo={}, base={}, head={}", repo, baseBranch, headBranch);
        try {
            return githubClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{base}...{head}",
                            properties.getDefaultOwner(), repo, baseBranch, headBranch)
                    .accept(MediaType.valueOf("application/vnd.github.diff"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch diff from GitHub: {}", e.getMessage());
            return "";
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> searchCode(String repo, String query) {
        log.debug("Searching code: repo={}, query={}", repo, query);
        try {
            Map<String, Object> response = githubClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/code")
                            .queryParam("q", query + " repo:" + properties.getDefaultOwner() + "/" + repo)
                            .queryParam("per_page", 20)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) return List.of();
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            if (items == null) return List.of();

            return items.stream()
                    .map(item -> (String) item.get("path"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("Failed to search code on GitHub: {}", msg);
            if (msg != null && (msg.contains("403") || msg.contains("rate"))) {
                // Return a message instead of empty — so the LLM stops retrying
                return List.of("RATE_LIMITED: GitHub code search rate limit exceeded. Use readSourceFile with specific file paths instead of searching.");
            }
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DeploymentRecord> getRecentDeployments(String repo, int limit) {
        log.debug("Fetching deployments: repo={}", repo);
        try {
            List<Map<String, Object>> response = githubClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/deployments")
                            .queryParam("per_page", limit)
                            .build(properties.getDefaultOwner(), repo))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (response == null) return List.of();
            return response.stream()
                    .map(d -> {
                        Map<String, Object> creator = (Map<String, Object>) d.get("creator");
                        return new DeploymentRecord(
                                String.valueOf(d.get("id")),
                                repo,
                                (String) d.get("ref"),
                                (String) d.get("sha"),
                                creator != null ? (String) creator.get("login") : "",
                                (String) d.getOrDefault("environment", ""),
                                Instant.parse((String) d.get("created_at"))
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch deployments from GitHub: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public String createBranch(String repo, String branchName, String fromRef) {
        log.info("Creating branch: repo={}, branch={}, from={}", repo, branchName, fromRef);
        try {
            // Get the SHA of the source ref
            Map<String, Object> refResponse = githubClient.get()
                    .uri("/repos/{owner}/{repo}/git/ref/heads/{ref}",
                            properties.getDefaultOwner(), repo, fromRef != null ? fromRef : "main")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (refResponse == null) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) refResponse.get("object");
            String sha = (String) object.get("sha");

            // Create the new branch
            githubClient.post()
                    .uri("/repos/{owner}/{repo}/git/refs", properties.getDefaultOwner(), repo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("ref", "refs/heads/" + branchName, "sha", sha))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            return branchName;
        } catch (Exception e) {
            log.error("Failed to create branch on GitHub: {}", e.getMessage());
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commitFiles(String repo, String branch, String message, Map<String, String> files) {
        log.info("Committing {} files to repo={}, branch={}", files.size(), repo, branch);
        for (var entry : files.entrySet()) {
            try {
                String encodedContent = Base64.getEncoder().encodeToString(entry.getValue().getBytes());

                // Fetch existing file SHA (required for updates)
                String sha = null;
                try {
                    Map<String, Object> existing = githubClient.get()
                            .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                                    properties.getDefaultOwner(), repo, entry.getKey(), branch)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .block();
                    if (existing != null) sha = (String) existing.get("sha");
                } catch (Exception ignored) {
                    // File doesn't exist yet — new file, no SHA needed
                }

                Map<String, Object> body = new java.util.HashMap<>();
                body.put("message", message);
                body.put("content", encodedContent);
                body.put("branch", branch);
                if (sha != null) body.put("sha", sha);

                githubClient.put()
                        .uri("/repos/{owner}/{repo}/contents/{path}",
                                properties.getDefaultOwner(), repo, entry.getKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                log.info("Committed file: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to commit file {} to GitHub: {}", entry.getKey(), e.getMessage());
                throw new RuntimeException("Commit failed for " + entry.getKey() + ": " + e.getMessage(), e);
            }
        }
    }

    // --- SrePlugin methods ---

    @Override
    public String getPluginId() { return "github"; }

    @Override
    public String getDisplayName() { return "GitHub"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public PluginType getType() { return PluginType.CODE_HOSTING; }

    @Override
    public PluginConfiguration getDefaultConfiguration() {
        return new PluginConfiguration(List.of(
                new ConfigField("token", "GitHub Token", "Personal access token or GitHub App token", ConfigFieldType.SECRET, true, ""),
                new ConfigField("defaultOwner", "Default Owner", "Default GitHub org/user", ConfigFieldType.STRING, true, "")
        ));
    }

    @Override
    public void initialize(Map<String, String> config) {
        log.info("Initializing GitHub plugin for owner: {}", properties.getDefaultOwner());
    }

    @Override
    public boolean validateConnection() {
        try {
            githubClient.get().uri("/user").retrieve().toBodilessEntity().block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isEnabled() { return properties.isEnabled(); }
}
