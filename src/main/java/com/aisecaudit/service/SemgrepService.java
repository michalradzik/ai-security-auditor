package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
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

    private final SemgrepResultParser parser =
        new SemgrepResultParser(new ObjectMapper());

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
            Map<String, String> originalPaths =
                writeFilesToTempDir(tempDir, filesContent);
            String jsonOutput = runSemgrep(tempDir);
            List<Finding> findings =
                parser.parse(jsonOutput, originalPaths);

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

    /** Writes the files flat into the temp dir and returns temp file name -> original path. */
    private Map<String, String> writeFilesToTempDir(
            Path tempDir,
            Map<String, String> filesContent)
            throws IOException {

        Map<String, String> originalPaths = new HashMap<>();
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
            originalPaths.put(safeFilename, entry.getKey());
        }
        return originalPaths;
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
