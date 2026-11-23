package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemgrepResultParserTest {

    private static final Map<String, String> ORIGINAL_PATHS =
            Map.of("src__main__java__UserController.java", "src/main/java/UserController.java");

    private final SemgrepResultParser parser = new SemgrepResultParser(new ObjectMapper());

    @Test
    void mapsResultAndRestoresRepositoryPath() {
        String json = """
                {"results": [{
                  "check_id": "java.lang.security.audit.formatted-sql-string",
                  "path": "/tmp/semgrep-audit-123/src__main__java__UserController.java",
                  "start": {"line": 13, "col": 9},
                  "extra": {"message": "SQL built from a string", "severity": "ERROR",
                            "lines": "String q = \\"SELECT * FROM users WHERE id = \\" + id;"}
                }], "errors": []}
                """;

        List<Finding> findings = parser.parse(json, ORIGINAL_PATHS);

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals("java.lang.security.audit.formatted-sql-string", finding.getRuleId());
        assertEquals("src/main/java/UserController.java", finding.getFilePath());
        assertEquals(Integer.valueOf(13), finding.getLineNumber());
        assertEquals(Finding.Severity.HIGH, finding.getSeverity());
        assertEquals("SQL built from a string", finding.getMessage());
        assertTrue(finding.getCodeSnippet().contains("SELECT"));
    }

    @Test
    void keepsReportedPathWhenFileIsUnknown() {
        assertEquals("/tmp/x/Other.java", SemgrepResultParser.originalPath("/tmp/x/Other.java", ORIGINAL_PATHS));
    }

    @Test
    void mapsClassicAndNewSeverityLevels() {
        assertEquals(Finding.Severity.CRITICAL, SemgrepResultParser.mapSeverity("CRITICAL"));
        assertEquals(Finding.Severity.HIGH, SemgrepResultParser.mapSeverity("error"));
        assertEquals(Finding.Severity.MEDIUM, SemgrepResultParser.mapSeverity("WARNING"));
        assertEquals(Finding.Severity.INFO, SemgrepResultParser.mapSeverity("INFO"));
        assertEquals(Finding.Severity.LOW, SemgrepResultParser.mapSeverity("something-new"));
    }

    @Test
    void truncatesLongSnippetsAndHandlesMissingLine() {
        String longCode = "x".repeat(800);
        String json = "{\"results\": [{\"check_id\": \"r\", \"path\": \"A.java\", "
                + "\"extra\": {\"lines\": \"" + longCode + "\"}}]}";

        Finding finding = parser.parse(json, Map.of()).get(0);

        assertEquals(SemgrepResultParser.MAX_SNIPPET_LENGTH, finding.getCodeSnippet().length());
        assertNull(finding.getLineNumber());
        assertEquals(Finding.Severity.MEDIUM, finding.getSeverity());
    }

    @Test
    void returnsEmptyListForEmptyOrInvalidOutput() {
        assertTrue(parser.parse("", Map.of()).isEmpty());
        assertTrue(parser.parse(null, Map.of()).isEmpty());
        assertTrue(parser.parse("{\"results\": []}", Map.of()).isEmpty());
        assertTrue(parser.parse("not json", Map.of()).isEmpty());
    }
}
