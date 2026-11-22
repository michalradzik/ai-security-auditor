package com.aisecaudit.report;

/**
 * One security problem reported by the AI.
 *
 * @param severity how serious the problem is
 * @param title    short name, e.g. "SQL injection in getUser"
 * @param file     path of the file in the repository
 * @param line     line number, or null if unknown
 * @param risk     how the problem can be exploited (1-2 sentences)
 * @param fix      corrected code
 */
public record AiFinding(
        ReportSeverity severity,
        String title,
        String file,
        Integer line,
        String risk,
        String fix) {
}
