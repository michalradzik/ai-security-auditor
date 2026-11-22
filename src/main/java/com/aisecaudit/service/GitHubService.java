package com.aisecaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    @Value("${app.github.token}")
    private String githubToken;

    @Value("${app.audit.max-files-per-pr:20}")
    private int maxFiles;

    @Value("${app.audit.max-file-size-chars:3000}")
    private int maxFileSize;

    private final WebClient webClient;

    public GitHubService(
            @Qualifier("githubWebClient")
            WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the content of changed Java/Kotlin files
     * of a pull request from GitHub.
     */
    public Map<String, String> getChangedFilesContent(
            String repoFullName,
            String baseSha,
            String headSha,
            List<String> changedFiles) {

        // Token check
        if (githubToken == null) {
            log.warn("GitHub token missing: " +
                    "set GITHUB_TOKEN in .env");
            return Collections.emptyMap();
        }

        Map<String, String> filesContent =
            new LinkedHashMap<>();

        // Use the given file list or fetch it from the API
        List<String> filesToProcess =
            (changedFiles != null
                    && !changedFiles.isEmpty())
                ? changedFiles.stream()
                    .filter(f -> f.endsWith(".java")
                              || f.endsWith(".kt"))
                    .limit(maxFiles)
                    .toList()
                : getChangedFilesFromApi(
                    repoFullName, baseSha, headSha);

        log.info("Fetching {} file(s) from GitHub",
                filesToProcess.size());

        for (String filePath : filesToProcess) {
            try {
                String content = getFileContent(
                    repoFullName, headSha, filePath);
                if (content != null
                        && !content.isBlank()) {
                    // Limit the file size
                    filesContent.put(filePath,
                        content.length() > maxFileSize
                            ? content.substring(
                                0, maxFileSize)
                            : content);
                }
            } catch (Exception e) {
                log.warn("Could not fetch file " +
                        "{}: {}", filePath,
                        e.getMessage());
            }
        }

        log.info("Fetched {} file(s) from GitHub",
                filesContent.size());
        return filesContent;
    }

    private List<String> getChangedFilesFromApi(
            String repoFullName,
            String baseSha,
            String headSha) {
        try {
            String compareUrl = String.format(
                "/repos/%s/compare/%s...%s",
                repoFullName, baseSha, headSha);

            Map<String, Object> result = webClient
                .get()
                .uri(compareUrl)
                .header("Authorization",
                    "Bearer " + githubToken)
                .retrieve()
                .bodyToMono(
                    new ParameterizedTypeReference<
                        Map<String, Object>>() {})
                .block();

            if (result == null) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files =
                (List<Map<String, Object>>)
                result.get("files");

            if (files == null) {
                return Collections.emptyList();
            }

            return files.stream()
                .map(f -> (String) f.get("filename"))
                .filter(Objects::nonNull)
                .filter(name -> name.endsWith(".java")
                             || name.endsWith(".kt"))
                .limit(maxFiles)
                .toList();

        } catch (Exception e) {
            log.error("Failed to fetch the file list: {}",
                     e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getFileContent(
            String repoFullName,
            String sha,
            String filePath) {
        try {
            String contentUrl = String.format(
                "/repos/%s/contents/%s?ref=%s",
                repoFullName, filePath, sha);

            Map<String, Object> response = webClient
                .get()
                .uri(contentUrl)
                .header("Authorization",
                    "Bearer " + githubToken)
                .retrieve()
                .bodyToMono(
                    new ParameterizedTypeReference<
                        Map<String, Object>>() {})
                .block();

            if (response == null
                    || !response.containsKey("content")) {
                return null;
            }

            // GitHub returns Base64 with line breaks
            String base64Content =
                ((String) response.get("content"))
                .replaceAll("\\s", "");

            return new String(
                Base64.getDecoder().decode(base64Content),
                StandardCharsets.UTF_8);

        } catch (WebClientResponseException e) {
            log.warn("GitHub API {} for {}: {}",
                    e.getStatusCode(),
                    filePath,
                    e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch {}: {}",
                    filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Posts the audit report as a PR comment.
     * Deletes the previous audit comment if there is one.
     */
    public void postPrComment(
            String repoFullName,
            Integer prNumber,
            String markdownBody) {

        if (githubToken == null) {
            log.warn("GitHub token missing: " +
                    "cannot post the comment");
            log.info("Audit report:\n{}", markdownBody);
            return;
        }

        try {
            // Delete the old audit comment
            deleteOldAuditComment(
                repoFullName, prNumber);

            // Post the new comment
            String commentUrl = String.format(
                "/repos/%s/issues/%d/comments",
                repoFullName, prNumber);

            webClient.post()
                .uri(commentUrl)
                .header("Authorization",
                    "Bearer " + githubToken)
                .bodyValue(Map.of("body", markdownBody))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            log.info("Report posted to PR #{}",
                    prNumber);

        } catch (Exception e) {
            log.error("Failed to post the comment " +
                    "to PR #{}: {}",
                    prNumber, e.getMessage());
        }
    }

    private void deleteOldAuditComment(
            String repoFullName,
            Integer prNumber) {
        try {
            String commentsUrl = String.format(
                "/repos/%s/issues/%d/comments",
                repoFullName, prNumber);

            List<Map<String, Object>> comments =
                webClient.get()
                    .uri(commentsUrl)
                    .header("Authorization",
                        "Bearer " + githubToken)
                    .retrieve()
                    .bodyToMono(
                        new ParameterizedTypeReference<
                            List<Map<String,Object>>>() {})
                    .block();

            if (comments == null) return;

            comments.stream()
                .filter(c -> {
                    String body = (String) c.get("body");
                    return body != null && body.contains(
                        "AI Security Audit Report");
                })
                .findFirst()
                .ifPresent(c -> {
                    Long commentId = ((Number)
                        c.get("id")).longValue();
                    String deleteUrl = String.format(
                        "/repos/%s/issues/comments/%d",
                        repoFullName, commentId);

                    webClient.delete()
                        .uri(deleteUrl)
                        .header("Authorization",
                            "Bearer " + githubToken)
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                    log.debug("Deleted the old " +
                            "audit comment");
                });

        } catch (Exception e) {
            log.warn("Could not delete the old " +
                    "comment: {}", e.getMessage());
        }
    }
}
