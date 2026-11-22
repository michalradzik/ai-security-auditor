package com.aisecaudit.report;

import java.util.Locale;

/** Severity levels used in reports, ordered from most to least serious. */
public enum ReportSeverity {
    CRITICAL("🔴", "Critical", "Krytyczne"),
    HIGH("🟠", "High", "Wysokie"),
    MEDIUM("🟡", "Medium", "Średnie"),
    LOW("🔵", "Low", "Niskie"),
    INFO("⚪", "Info", "Informacyjne");

    private final String icon;
    private final String labelEn;
    private final String labelPl;

    ReportSeverity(String icon, String labelEn, String labelPl) {
        this.icon = icon;
        this.labelEn = labelEn;
        this.labelPl = labelPl;
    }

    public String icon() {
        return icon;
    }

    public String label(ReportLanguage language) {
        return language == ReportLanguage.PL ? labelPl : labelEn;
    }

    /** Lenient parsing of model output: unknown or missing values become INFO. */
    public static ReportSeverity parse(String value) {
        if (value == null) {
            return INFO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
