package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
import com.aisecaudit.security.SecretMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns Semgrep's {@code --json} output into {@link Finding}s.
 * Semgrep scans copies of the files in a temp directory, so the reported paths
 * are mapped back to the original repository paths.
 */
class SemgrepResultParser {

    private static final Logger log = LoggerFactory.getLogger(SemgrepResultParser.class);
    static final int MAX_SNIPPET_LENGTH = 500;

    private final ObjectMapper objectMapper;

    SemgrepResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param json             Semgrep JSON output
     * @param originalPathByTempName file name in the temp directory -> path in the repository
     */
    List<Finding> parse(String json, Map<String, String> originalPathByTempName) {
        List<Finding> findings = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return findings;
        }
        try {
            JsonNode results = objectMapper.readTree(json).path("results");
            if (!results.isArray()) {
                return findings;
            }
            for (JsonNode result : results) {
                findings.add(toFinding(result, originalPathByTempName));
            }
        } catch (Exception e) {
            log.error("Failed to parse Semgrep JSON: {}", e.getMessage());
        }
        return findings;
    }

    private Finding toFinding(JsonNode result, Map<String, String> originalPathByTempName) {
        JsonNode extra = result.path("extra");
        // mask before truncating, so a cut never leaves half a secret unmatched
        String snippet = SecretMasker.mask(extra.path("lines").asText(""));
        if (snippet.length() > MAX_SNIPPET_LENGTH) {
            snippet = snippet.substring(0, MAX_SNIPPET_LENGTH);
        }
        int line = result.path("start").path("line").asInt(0);

        return Finding.builder()
                .ruleId(result.path("check_id").asText("unknown"))
                .message(extra.path("message").asText(""))
                .severity(mapSeverity(extra.path("severity").asText("WARNING")))
                .filePath(originalPath(result.path("path").asText(""), originalPathByTempName))
                .lineNumber(line > 0 ? line : null)
                .codeSnippet(snippet)
                .isFalsePositive(false)
                .build();
    }

    static String originalPath(String reportedPath, Map<String, String> originalPathByTempName) {
        if (reportedPath.isEmpty()) {
            return reportedPath;
        }
        Path fileName = Path.of(reportedPath).getFileName();
        String original = fileName == null ? null : originalPathByTempName.get(fileName.toString());
        return original != null ? original : reportedPath;
    }

    /** Covers the classic levels (ERROR/WARNING/INFO) and the newer ones (CRITICAL/HIGH/MEDIUM/LOW). */
    static Finding.Severity mapSeverity(String semgrepSeverity) {
        return switch (semgrepSeverity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> Finding.Severity.CRITICAL;
            case "ERROR", "HIGH" -> Finding.Severity.HIGH;
            case "WARNING", "MEDIUM" -> Finding.Severity.MEDIUM;
            case "INFO" -> Finding.Severity.INFO;
            default -> Finding.Severity.LOW;
        };
    }
}
