package com.aisecaudit.report;

import java.util.Locale;

public enum ReportLanguage {
    EN("English"),
    PL("Polish");

    private final String englishName;

    ReportLanguage(String englishName) {
        this.englishName = englishName;
    }

    /** Language name used in the prompt ("write all texts in ..."). */
    public String englishName() {
        return englishName;
    }

    public static ReportLanguage parse(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith("pl") ? PL : EN;
    }
}
