package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SemgrepService {

    private static final Logger log = LoggerFactory.getLogger(SemgrepService.class);

    @Value("${app.semgrep.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${app.semgrep.enabled:true}")
    private boolean semgrepEnabled;

    private final List<String> rules = List.of(
            "p/java",
            "p/owasp-top-ten",
            "p/spring-security",
            "p/secrets"
    );

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    /**
     * Analyzes the given files with Semgrep.
     * Returns an empty list when Semgrep is not
     * installed or disabled.
     */
    public List<Finding> analyze(
            Map<String, String> filesContent) {

        if (!semgrepEnabled) {
            log.info("Semgrep disabled " +
                    "(app.semgrep.enabled=false)");
            return Collections.emptyList();
        }

        if (filesContent.isEmpty()) {
            log.info("No files for Semgrep to analyze");
            return Collections.emptyList();
        }

        if (!isSemgrepAvailable()) {
            log.warn("Semgrep is not installed. " +
                    "Skipping static analysis. " +
                    "Install it with: pip install semgrep");
            return Collections.emptyList();
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory(
                "semgrep-audit-");
            writeFilesToTempDir(tempDir, filesContent);
            String jsonOutput = runSemgrep(tempDir);
            List<Finding> findings =
                parseSemgrepJson(jsonOutput);

            log.info("Semgrep: {} potential issue(s) " +
                    "in {} file(s)",
                    findings.size(),
                    filesContent.size());
            return findings;

        } catch (Exception e) {
            log.error("Semgrep error: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }
    }

    private boolean isSemgrepAvailable() {
        try {
            Process process = new ProcessBuilder(
                "semgrep", "--version")
                .start();
            boolean finished = process.waitFor(
                5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void writeFilesToTempDir(
            Path tempDir,
            Map<String, String> filesContent)
            throws IOException {

        for (Map.Entry<String, String> entry :
                filesContent.entrySet()) {
            // Flatten the path (replace / with __)
            String safeFilename = entry.getKey()
                .replace("/", "__")
                .replace("\\", "__");

            Path filePath = tempDir.resolve(safeFilename);
            Files.writeString(filePath,
                entry.getValue(),
                StandardCharsets.UTF_8);
        }
    }

    private String runSemgrep(Path tempDir)
            throws Exception {

        List<String> command = new ArrayList<>();
        command.add("semgrep");
        command.add("--json");
        command.add("--quiet");
        command.add("--no-git-ignore");

        for (String rule : rules) {
            command.add("--config");
            command.add(rule);
        }
        command.add(tempDir.toString());

        log.debug("Running Semgrep: {}",
                String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout = new String(
            process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        String stderr = new String(
            process.getErrorStream().readAllBytes(),
            StandardCharsets.UTF_8);

        boolean finished = process.waitFor(
            timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            log.warn("Semgrep timed out after {}s",
                    timeoutSeconds);
            return "{\"results\": []}";
        }

        int exitCode = process.exitValue();
        // exit code 1 = findings reported (expected)
        // exit code 2+ = real error
        if (exitCode > 1 && !stderr.isEmpty()) {
            log.warn("Semgrep stderr: {}", stderr);
        }

        return stdout.isBlank()
            ? "{\"results\": []}"
            : stdout;
    }

    private List<Finding> parseSemgrepJson(
            String jsonOutput) {

        List<Finding> findings = new ArrayList<>();
        try {
            JsonNode root =
                objectMapper.readTree(jsonOutput);
            JsonNode results = root.path("results");

            if (!results.isArray()) {
                return findings;
            }

            for (JsonNode result : results) {
                Finding finding = mapToFinding(result);
                if (finding != null) {
                    findings.add(finding);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Semgrep JSON: {}",
                     e.getMessage());
        }
        return findings;
    }

    private Finding mapToFinding(JsonNode result) {
        try {
            String ruleId = result
                .path("check_id").asText("unknown");
            String message = result
                .path("extra")
                .path("message").asText("");
            String filePath = result
                .path("path").asText("");
            int line = result
                .path("start")
                .path("line").asInt(0);
            String severity = result
                .path("extra")
                .path("severity").asText("WARNING");
            String snippet = result
                .path("extra")
                .path("lines").asText("");

            // Limit the snippet to 500 characters
            if (snippet.length() > 500) {
                snippet = snippet.substring(0, 500);
            }

            return Finding.builder()
                .ruleId(ruleId)
                .message(message)
                .severity(mapSeverity(severity))
                .filePath(filePath)
                .lineNumber(line)
                .codeSnippet(snippet)
                .isFalsePositive(false)
                .build();

        } catch (Exception e) {
            log.warn("Could not map a finding: {}",
                    e.getMessage());
            return null;
        }
    }

    private Finding.Severity mapSeverity(
            String semgrepSeverity) {
        return switch (semgrepSeverity.toUpperCase()) {
            case "ERROR"   -> Finding.Severity.HIGH;
            case "WARNING" -> Finding.Severity.MEDIUM;
            case "INFO"    -> Finding.Severity.LOW;
            default        -> Finding.Severity.LOW;
        };
    }

    private void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            log.warn("Could not clean up the temp dir: {}",
                    e.getMessage());
        }
    }
}
