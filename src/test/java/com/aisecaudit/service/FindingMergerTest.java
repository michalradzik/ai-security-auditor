package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingMergerTest {

    private final FindingMerger merger = new FindingMerger();

    private static Finding semgrep(String ruleId, String file, Integer line, Finding.Severity severity) {
        return Finding.builder().ruleId(ruleId).filePath(file).lineNumber(line).severity(severity)
                .message("semgrep message").build();
    }

    private static Finding ai(String title, String file, Integer line, Finding.Severity severity) {
        return Finding.builder().ruleId("ai:" + title).message(title).filePath(file).lineNumber(line)
                .severity(severity).aiExplanation("risk from AI").suggestedFix("fixed code").build();
    }

    @Test
    void foldsAiFindingOnTheSameLineIntoSemgrepFinding() {
        Finding s = semgrep("java.sql-injection", "src/A.java", 13, Finding.Severity.HIGH);
        Finding a = ai("SQL injection", "src/A.java", 13, Finding.Severity.CRITICAL);

        List<Finding> merged = merger.merge(List.of(s), List.of(a), List.of());

        assertEquals(1, merged.size());
        assertSame(s, merged.get(0));
        assertEquals("java.sql-injection", s.getRuleId());
        assertEquals("risk from AI", s.getAiExplanation());
        assertEquals("fixed code", s.getSuggestedFix());
        assertEquals(Finding.Severity.CRITICAL, s.getSeverity());
    }

    @Test
    void matchesWithinLineToleranceButNotBeyond() {
        Finding s = semgrep("rule", "src/A.java", 13, Finding.Severity.MEDIUM);

        assertEquals(1, merger.merge(List.of(s), List.of(ai("near", "src/A.java", 15, Finding.Severity.LOW)), List.of()).size());

        Finding s2 = semgrep("rule", "src/A.java", 13, Finding.Severity.MEDIUM);
        assertEquals(2, merger.merge(List.of(s2), List.of(ai("far", "src/A.java", 16, Finding.Severity.LOW)), List.of()).size());
    }

    @Test
    void keepsFindingsFromDifferentFilesOrWithoutLine() {
        Finding s = semgrep("rule", "src/A.java", 13, Finding.Severity.HIGH);

        List<Finding> merged = merger.merge(List.of(s),
                List.of(ai("other file", "src/B.java", 13, Finding.Severity.HIGH),
                        ai("no line", "src/A.java", null, Finding.Severity.LOW)),
                List.of());

        assertEquals(3, merged.size());
        assertEquals(Finding.Severity.HIGH, s.getSeverity());
    }

    @Test
    void marksFalsePositivesAndDoesNotMergeIntoThem() {
        Finding s = semgrep("java.hardcoded-secret", "src/A.java", 5, Finding.Severity.HIGH);
        Finding a = ai("Something else", "src/A.java", 5, Finding.Severity.MEDIUM);

        List<Finding> merged = merger.merge(List.of(s), List.of(a),
                List.of("java.hardcoded-secret: test fixture, not a real key"));

        assertTrue(s.getIsFalsePositive());
        assertEquals(2, merged.size());
        assertEquals(null, s.getAiExplanation());
    }

    @Test
    void picksTheClosestSemgrepFinding() {
        Finding far = semgrep("far", "src/A.java", 10, Finding.Severity.LOW);
        Finding close = semgrep("close", "src/A.java", 12, Finding.Severity.LOW);

        merger.merge(List.of(far, close), List.of(ai("x", "src/A.java", 12, Finding.Severity.LOW)), List.of());

        assertEquals("risk from AI", close.getAiExplanation());
        assertEquals(null, far.getAiExplanation());
        assertFalse(Boolean.TRUE.equals(far.getIsFalsePositive()));
    }

    @Test
    void moreSevereUsesEnumOrder() {
        assertEquals(Finding.Severity.CRITICAL, FindingMerger.moreSevere(Finding.Severity.LOW, Finding.Severity.CRITICAL));
        assertEquals(Finding.Severity.HIGH, FindingMerger.moreSevere(Finding.Severity.HIGH, Finding.Severity.MEDIUM));
        assertEquals(Finding.Severity.INFO, FindingMerger.moreSevere(null, Finding.Severity.INFO));
    }
}
