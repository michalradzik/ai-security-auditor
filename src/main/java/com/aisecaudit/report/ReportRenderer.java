package com.aisecaudit.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.List;

/**
 * Renders an {@link AiReport} as GitHub-flavored Markdown.
 * The layout is fixed in code, so every report looks the same regardless of how the model phrases its answer.
 */
@Component
public class ReportRenderer {

    /** GitHubService finds and replaces the previous audit comment by this text. Do not change. */
    public static final String REPORT_MARKER = "AI Security Audit Report";

    private final ReportLanguage language;
    private final String model;

    public ReportRenderer(@Value("${app.report.language:en}") String language,
                          @Value("${app.claude.model:unknown}") String model) {
        this.language = ReportLanguage.parse(language);
        this.model = model;
    }

    public ReportLanguage language() {
        return language;
    }

    public String render(AiReport report, ReportContext context) {
        StringBuilder md = new StringBuilder();
        md.append("## 🔒 ").append(REPORT_MARKER).append("\n\n");

        if (context.demo()) {
            md.append("> ⚠️ ").append(t(
                    "**Demo mode:** no Claude API key configured, the AI analysis was skipped.",
                    "**Tryb demo:** brak klucza Claude API, analiza AI została pominięta.")).append("\n\n");
        }

        md.append("**").append(verdict(report, context)).append("**\n\n");
        if (!report.summary().isEmpty()) {
            md.append(inline(report.summary())).append("\n\n");
        }

        appendSeverityTable(md, report);
        appendFindings(md, report, context);
        appendList(md, "✅ " + t("False positives", "Fałszywe alarmy"), report.falsePositives(), false);
        appendList(md, "📋 " + t("Fix first", "Napraw w pierwszej kolejności"), report.priorities(), true);
        appendFooter(md, context);
        return md.toString();
    }

    private String verdict(AiReport report, ReportContext context) {
        long critical = report.count(ReportSeverity.CRITICAL);
        long high = report.count(ReportSeverity.HIGH);
        long medium = report.count(ReportSeverity.MEDIUM);
        if (critical > 0) {
            return "🔴 " + t(critical + (critical == 1 ? " critical issue" : " critical issues") + ": do not merge",
                    "Krytyczne problemy: " + critical + ", nie łącz tego PR");
        }
        if (high > 0) {
            return "🟠 " + t(high + (high == 1 ? " high-severity issue" : " high-severity issues") + ": fix before merging",
                    "Poważne problemy: " + high + ", popraw przed połączeniem");
        }
        if (medium > 0) {
            return "🟡 " + t(medium + (medium == 1 ? " medium issue" : " medium issues") + " worth fixing",
                    "Średnie problemy do poprawy: " + medium);
        }
        if (context.demo()) {
            return "⚪ " + t("No AI analysis in demo mode", "Brak analizy AI w trybie demo");
        }
        return "✅ " + t("No significant security issues found", "Nie znaleziono istotnych problemów bezpieczeństwa");
    }

    private void appendSeverityTable(StringBuilder md, AiReport report) {
        md.append("| ").append(t("Severity", "Waga")).append(" | ").append(t("Issues", "Liczba")).append(" |\n");
        md.append("| --- | ---: |\n");
        for (ReportSeverity severity : ReportSeverity.values()) {
            md.append("| ").append(severity.icon()).append(' ').append(severity.label(language))
                    .append(" | ").append(report.count(severity)).append(" |\n");
        }
        md.append('\n');
    }

    private void appendFindings(StringBuilder md, AiReport report, ReportContext context) {
        if (report.findings().isEmpty()) {
            return;
        }
        md.append("### ").append(t("Findings", "Znalezione problemy")).append("\n\n");
        int number = 1;
        for (AiFinding finding : report.findings()) {
            ReportSeverity severity = finding.severity();
            md.append("#### ").append(number++).append(". ").append(severity.icon()).append(' ')
                    .append(inline(finding.title())).append("\n\n");

            md.append("**").append(severity.label(language)).append("**");
            String location = location(finding, context);
            if (location != null) {
                md.append(" · ").append(location);
            }
            md.append("\n\n");

            if (finding.risk() != null) {
                md.append(inline(finding.risk())).append("\n\n");
            }
            if (finding.fix() != null) {
                String fence = finding.fix().contains("```") ? "~~~" : "```";
                md.append("<details><summary>").append(t("Suggested fix", "Proponowana poprawka"))
                        .append("</summary>\n\n")
                        .append(fence).append(codeLanguage(finding.file())).append('\n')
                        .append(finding.fix().strip()).append('\n')
                        .append(fence).append("\n\n</details>\n\n");
            }
        }
    }

    private String location(AiFinding finding, ReportContext context) {
        if (finding.file() == null) {
            return null;
        }
        String file = finding.file().replace("`", "");
        String label = finding.line() != null ? file + ":" + finding.line() : file;
        if (!context.hasRepository()) {
            return "`" + label + "`";
        }
        String url = "https://github.com/" + context.repoFullName() + "/blob/" + context.headSha() + "/"
                + file.replace(" ", "%20") + (finding.line() != null ? "#L" + finding.line() : "");
        return "[`" + label + "`](" + url + ")";
    }

    private void appendList(StringBuilder md, String heading, List<String> items, boolean numbered) {
        if (items.isEmpty()) {
            return;
        }
        md.append("### ").append(heading).append("\n\n");
        for (int i = 0; i < items.size(); i++) {
            md.append(numbered ? (i + 1) + ". " : "- ").append(inline(items.get(i))).append('\n');
        }
        md.append('\n');
    }

    private void appendFooter(StringBuilder md, ReportContext context) {
        StringBuilder footer = new StringBuilder("AI Code Security Auditor");
        if (context.headSha() != null && !context.headSha().isBlank()) {
            footer.append(" · commit `").append(context.headSha(), 0, Math.min(7, context.headSha().length()))
                    .append('`');
        }
        footer.append(" · ").append(context.filesAnalyzed()).append(' ').append(filesWord(context.filesAnalyzed()));
        if (!context.demo()) {
            footer.append(" · ").append(context.tokensUsed()).append(t(" tokens", " tokenów"));
            if (context.costUsd() != null) {
                footer.append(" (~$").append(context.costUsd().setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .append(')');
            }
            footer.append(" · ").append(model);
        }
        md.append("---\n<sub>").append(footer).append("</sub>\n");
    }

    /** Text from the model goes into Markdown: keep it on one line and stop it from injecting HTML. */
    private static String inline(String text) {
        return text.replace("<", "&lt;").replace(">", "&gt;").replaceAll("\\s*\\R\\s*", " ").trim();
    }

    private static String codeLanguage(String file) {
        if (file == null) {
            return "java";
        }
        if (file.endsWith(".kt") || file.endsWith(".kts")) {
            return "kotlin";
        }
        if (file.endsWith(".yml") || file.endsWith(".yaml")) {
            return "yaml";
        }
        if (file.endsWith(".xml")) {
            return "xml";
        }
        return "java";
    }

    private String filesWord(int count) {
        if (language != ReportLanguage.PL) {
            return count == 1 ? "file" : "files";
        }
        int lastTwo = count % 100;
        int last = count % 10;
        if (count == 1) {
            return "plik";
        }
        return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? "pliki" : "plików";
    }

    private String t(String en, String pl) {
        return language == ReportLanguage.PL ? pl : en;
    }
}
