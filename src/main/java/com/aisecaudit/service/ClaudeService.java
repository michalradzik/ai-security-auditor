package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
import com.aisecaudit.report.AiFinding;
import com.aisecaudit.report.AiReport;
import com.aisecaudit.report.AiReportParser;
import com.aisecaudit.report.ReportContext;
import com.aisecaudit.report.ReportRenderer;
import com.aisecaudit.report.ReportSeverity;
import com.aisecaudit.security.SecretMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends the code and the Semgrep results to Claude and turns the answer into a report.
 * Claude is asked for JSON; the Markdown layout is produced by {@link ReportRenderer}.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final int MAX_FILES_IN_PROMPT = 5;
    private static final int MAX_FILE_CHARS_IN_PROMPT = 2500;
    private static final int MAX_SEMGREP_FINDINGS_IN_PROMPT = 10;

    private final WebClient webClient;
    private final AiReportParser parser;
    private final ReportRenderer renderer;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClaudeService(@Qualifier("claudeWebClient") WebClient webClient,
                         AiReportParser parser,
                         ReportRenderer renderer,
                         @Value("${app.claude.api-key:}") String apiKey,
                         @Value("${app.claude.model:claude-sonnet-4-6}") String model,
                         @Value("${app.claude.max-tokens:4000}") int maxTokens) {
        this.webClient = webClient;
        this.parser = parser;
        this.renderer = renderer;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Result of the analysis.
     *
     * @param markdownReport report ready to post (rendered without repository links)
     * @param summary        short summary stored with the audit
     * @param tokensUsed     Claude tokens used (0 in demo mode)
     * @param report         structured report, or null if the model did not return valid JSON
     */
    public record AnalysisResult(String markdownReport, String summary, int tokensUsed, AiReport report) {

        public AnalysisResult(String markdownReport, String summary, int tokensUsed) {
            this(markdownReport, summary, tokensUsed, null);
        }

        public boolean isDemo() {
            return tokensUsed == 0;
        }
    }

    public AnalysisResult analyze(Map<String, String> filesContent, List<Finding> semgrepFindings) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("CLAUDE_API_KEY is not set: returning a demo report without AI analysis");
            return demoResult(filesContent.size(), semgrepFindings);
        }

        String prompt = buildPrompt(filesContent, semgrepFindings);
        log.info("Sending {} file(s) to Claude (~{} tokens)", filesContent.size(), prompt.length() / 4);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));

            Map<String, Object> response = webClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofSeconds(90))
                    .block();

            return parseResponse(response, filesContent.size());

        } catch (WebClientResponseException e) {
            log.error("Claude API HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return errorResult("Claude API error " + e.getStatusCode(), filesContent.size(), semgrepFindings);
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            return errorResult(e.getMessage(), filesContent.size(), semgrepFindings);
        }
    }

    String buildPrompt(Map<String, String> filesContent, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a senior application security engineer specialised in Java and Spring Boot
                and in the vulnerabilities typical for AI-generated code (Copilot, Cursor, Claude Code).

                Check especially for:
                1. SQL injection (string concatenation instead of prepared statements)
                2. REST endpoints without authorization (@PreAuthorize, security config)
                3. Hardcoded secrets, tokens and passwords
                4. Missing input validation (@Valid, @NotNull on DTOs)
                5. Mass assignment (entities bound directly from requests)
                6. Stack traces or internal details in responses
                7. CORS wildcard configuration
                8. Missing rate limiting on authentication endpoints
                9. Open redirects
                10. Incorrect exception handling

                """);

        if (findings.isEmpty()) {
            sb.append("## SEMGREP RESULTS\nNo automated findings (or Semgrep disabled). Review the code manually.\n\n");
        } else {
            sb.append("## SEMGREP RESULTS\n\n");
            int count = 0;
            for (Finding f : findings) {
                if (count++ >= MAX_SEMGREP_FINDINGS_IN_PROMPT) {
                    sb.append("... and ").append(findings.size() - MAX_SEMGREP_FINDINGS_IN_PROMPT)
                            .append(" more\n\n");
                    break;
                }
                String snippet = f.getCodeSnippet() == null ? "N/A"
                        : f.getCodeSnippet().substring(0, Math.min(150, f.getCodeSnippet().length()))
                        .replace("\n", " ");
                sb.append("- [").append(f.getSeverity()).append("] ").append(f.getRuleId())
                        .append(" in ").append(f.getFilePath())
                        .append(" line ").append(f.getLineNumber() != null ? f.getLineNumber() : 0)
                        .append(": ").append(f.getMessage())
                        .append(" | code: ").append(snippet).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## CODE TO REVIEW\n\n");
        int fileCount = 0;
        for (Map.Entry<String, String> entry : filesContent.entrySet()) {
            if (fileCount++ >= MAX_FILES_IN_PROMPT) {
                sb.append("[remaining files skipped: max ").append(MAX_FILES_IN_PROMPT).append(" per audit]\n\n");
                break;
            }
            // secret values never leave our infrastructure; line numbers stay the same
            String content = SecretMasker.mask(entry.getValue());
            if (content.length() > MAX_FILE_CHARS_IN_PROMPT) {
                content = content.substring(0, MAX_FILE_CHARS_IN_PROMPT)
                        + "\n// ... [truncated to " + MAX_FILE_CHARS_IN_PROMPT + " characters]";
            }
            sb.append("### ").append(entry.getKey()).append("\n```\n").append(addLineNumbers(content))
                    .append("\n```\n\n");
        }

        sb.append("""
                ## YOUR TASK
                1. Assess every Semgrep finding: real problem or false positive.
                2. Find additional vulnerabilities that Semgrep missed.
                3. For every real problem give: severity, a short title, the file, the line number
                   (use the numbers shown at the start of each code line), how it can be exploited
                   in 1-2 sentences, and the corrected code.

                ## ANSWER FORMAT
                Answer with ONE JSON object and nothing else (no Markdown, no text before or after):
                {
                  "summary": "2-3 sentences: overall security assessment of this change",
                  "findings": [
                    {
                      "severity": "CRITICAL | HIGH | MEDIUM | LOW | INFO",
                      "title": "short name, e.g. SQL injection in getUser",
                      "file": "path of the file exactly as given above",
                      "line": 13,
                      "risk": "how an attacker can exploit it",
                      "fix": "corrected code only, no Markdown fences"
                    }
                  ],
                  "falsePositives": ["Semgrep rule id: why it is not a problem"],
                  "priorities": ["most important thing to fix first", "second", "third"]
                }
                Use an empty array when there is nothing to report.
                """);
        sb.append("Write all texts (summary, title, risk, falsePositives, priorities) in ")
                .append(renderer.language().englishName())
                .append(". Keep code and identifiers unchanged.\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private AnalysisResult parseResponse(Map<String, Object> response, int filesAnalyzed) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        String text = (String) content.get(0).get("text");

        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        int tokens = ((Number) usage.get("input_tokens")).intValue()
                + ((Number) usage.get("output_tokens")).intValue();
        if ("max_tokens".equals(response.get("stop_reason"))) {
            log.warn("Claude answer was cut at max_tokens={}; consider raising app.claude.max-tokens", maxTokens);
        }
        log.info("Claude API: {} tokens", tokens);

        Optional<AiReport> report = parser.parse(text);
        if (report.isEmpty()) {
            // Model did not return valid JSON: still post its answer, just without our layout
            String summary = text.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("*"))
                    .findFirst()
                    .orElse("Analysis finished");
            return new AnalysisResult("## 🔒 " + ReportRenderer.REPORT_MARKER + "\n\n" + text, summary, tokens);
        }

        AiReport aiReport = report.get();
        String markdown = renderer.render(aiReport, ReportContext.inline(filesAnalyzed, tokens, false));
        return new AnalysisResult(markdown, aiReport.summary(), tokens, aiReport);
    }

    /** Demo report built from Semgrep findings, used when no API key is configured. */
    private AnalysisResult demoResult(int filesAnalyzed, List<Finding> semgrepFindings) {
        AiReport report = new AiReport(
                renderer.language() == com.aisecaudit.report.ReportLanguage.PL
                        ? "Analiza AI pominięta. Poniżej tylko wyniki Semgrep (jeśli jest włączony)."
                        : "AI analysis skipped. Only Semgrep results are listed below (if Semgrep is enabled).",
                fromSemgrep(semgrepFindings), List.of(), List.of());
        String markdown = renderer.render(report, ReportContext.inline(filesAnalyzed, 0, true));
        return new AnalysisResult(markdown, report.summary(), 0, report);
    }

    private AnalysisResult errorResult(String error, int filesAnalyzed, List<Finding> semgrepFindings) {
        AiReport report = new AiReport(
                "AI analysis failed: " + error + ". Semgrep results are listed below.",
                fromSemgrep(semgrepFindings), List.of(), List.of());
        String markdown = renderer.render(report, ReportContext.inline(filesAnalyzed, 0, false));
        return new AnalysisResult(markdown, report.summary(), 0, report);
    }

    private static List<AiFinding> fromSemgrep(List<Finding> findings) {
        return findings.stream()
                .map(f -> new AiFinding(
                        f.getSeverity() == null ? ReportSeverity.INFO : ReportSeverity.parse(f.getSeverity().name()),
                        f.getRuleId() != null ? f.getRuleId() : "Semgrep finding",
                        f.getFilePath(),
                        f.getLineNumber(),
                        f.getMessage(),
                        null))
                .toList();
    }

    /** Line numbers let the model report exact lines, which become links in the report. */
    static String addLineNumbers(String code) {
        String[] lines = code.split("\\R", -1);
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            numbered.append(String.format("%4d | ", i + 1)).append(lines[i]);
            if (i < lines.length - 1) {
                numbered.append('\n');
            }
        }
        return numbered.toString();
    }
}
