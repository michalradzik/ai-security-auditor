package com.aisecaudit.report;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReportParserTest {

    private final AiReportParser parser = new AiReportParser();

    @Test
    void parsesJsonInsideMarkdownFenceAndSortsBySeverity() {
        String answer = """
                Here is the result:
                ```json
                {
                  "summary": "Two problems.",
                  "findings": [
                    {"severity": "HIGH", "title": "Missing auth", "file": "A.java", "line": 19, "risk": "r", "fix": "f"},
                    {"severity": "critical", "title": "SQL injection", "file": "A.java", "line": 13}
                  ],
                  "falsePositives": ["rule-x: test code"],
                  "priorities": ["Fix SQL"]
                }
                ```
                """;

        AiReport report = parser.parse(answer).orElseThrow();

        assertEquals("Two problems.", report.summary());
        assertEquals(2, report.findings().size());
        assertEquals(ReportSeverity.CRITICAL, report.findings().get(0).severity());
        assertEquals(Integer.valueOf(13), report.findings().get(0).line());
        assertEquals(1, report.falsePositives().size());
        assertEquals(1, report.priorities().size());
    }

    @Test
    void unknownSeverityBecomesInfo() {
        AiReport report = parser.parse("""
                {"summary": "s", "findings": [{"severity": "SEVERE?", "title": "T", "file": "B.java"}]}
                """).orElseThrow();

        assertEquals(ReportSeverity.INFO, report.findings().get(0).severity());
    }

    @Test
    void skipsFindingsWithoutTitle() {
        AiReport report = parser.parse("""
                {"summary": "s", "findings": [{"severity": "HIGH"}, {"severity": "LOW", "title": "Kept"}]}
                """).orElseThrow();

        assertEquals(1, report.findings().size());
        assertEquals("Kept", report.findings().get(0).title());
    }

    @Test
    void returnsEmptyForInvalidOrMissingJson() {
        assertTrue(parser.parse("not json at all").isEmpty());
        assertTrue(parser.parse("{ broken").isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertEquals(Optional.empty(), parser.parse(null));
    }
}
