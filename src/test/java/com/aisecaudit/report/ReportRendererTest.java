package com.aisecaudit.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRendererTest {

    private static final AiReport REPORT = new AiReport(
            "Summary text",
            List.of(new AiFinding(ReportSeverity.CRITICAL, "SQL injection", "src/A.java", 13,
                    "Attacker can <script>alert(1)</script> read data", "String sql = \"?\";")),
            List.of("rule-x: test code"),
            List.of("Fix SQL first"));

    @Test
    void rendersVerdictTableLinksAndFix() {
        String md = new ReportRenderer("en", "test-model").render(REPORT,
                new ReportContext("owner/repo", "abcdef1234567", 1, 1200, new BigDecimal("0.0108"), false));

        assertTrue(md.contains(ReportRenderer.REPORT_MARKER));
        assertTrue(md.contains("1 critical issue: do not merge"));
        assertTrue(md.contains("| 🔴 Critical | 1 |"));
        assertTrue(md.contains("https://github.com/owner/repo/blob/abcdef1234567/src/A.java#L13"));
        assertTrue(md.contains("<details><summary>Suggested fix</summary>"));
        assertTrue(md.contains("```java"));
        assertTrue(md.contains("commit `abcdef1`"));
        assertTrue(md.contains("1200 tokens (~$0.0108) · test-model"));
    }

    @Test
    void escapesHtmlFromModelText() {
        String md = new ReportRenderer("en", "m").render(REPORT, ReportContext.inline(1, 10, false));

        assertFalse(md.contains("<script>"));
        assertTrue(md.contains("&lt;script&gt;"));
    }

    @Test
    void withoutRepositoryLocationIsPlainCode() {
        String md = new ReportRenderer("en", "m").render(REPORT, ReportContext.inline(1, 10, false));

        assertTrue(md.contains("`src/A.java:13`"));
        assertFalse(md.contains("https://github.com/"));
    }

    @Test
    void demoModeDoesNotClaimTheCodeIsSafe() {
        AiReport empty = new AiReport("s", List.of(), List.of(), List.of());
        String md = new ReportRenderer("en", "m").render(empty, ReportContext.inline(1, 0, true));

        assertTrue(md.contains("Demo mode"));
        assertFalse(md.contains("No significant security issues found"));
    }

    @Test
    void polishLabelsAndPlurals() {
        AiReport empty = new AiReport("s", List.of(), List.of(), List.of());
        ReportRenderer pl = new ReportRenderer("pl", "m");

        assertTrue(pl.render(REPORT, ReportContext.inline(1, 10, false)).contains("Krytyczne problemy: 1"));
        assertTrue(pl.render(empty, ReportContext.inline(1, 10, false)).contains("1 plik "));
        assertTrue(pl.render(empty, ReportContext.inline(3, 10, false)).contains("3 pliki "));
        assertTrue(pl.render(empty, ReportContext.inline(12, 10, false)).contains("12 plików "));
    }
}
