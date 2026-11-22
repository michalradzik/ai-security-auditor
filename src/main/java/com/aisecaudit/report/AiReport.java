package com.aisecaudit.report;

import java.util.Comparator;
import java.util.List;

/** Structured result of the AI analysis. Rendering is done separately by {@link ReportRenderer}. */
public record AiReport(
        String summary,
        List<AiFinding> findings,
        List<String> falsePositives,
        List<String> priorities) {

    public AiReport {
        summary = summary == null ? "" : summary.trim();
        findings = findings == null ? List.of() : findings.stream()
                .sorted(Comparator.comparing(AiFinding::severity)
                        .thenComparing(f -> f.file() == null ? "" : f.file())
                        .thenComparing(f -> f.line() == null ? 0 : f.line()))
                .toList();
        falsePositives = falsePositives == null ? List.of() : List.copyOf(falsePositives);
        priorities = priorities == null ? List.of() : List.copyOf(priorities);
    }

    public long count(ReportSeverity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }
}
