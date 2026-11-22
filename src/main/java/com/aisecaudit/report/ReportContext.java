package com.aisecaudit.report;

import java.math.BigDecimal;

/**
 * Facts about the audit that the AI does not know, shown in the report.
 *
 * @param repoFullName "owner/repo", or null for inline test audits (no links)
 * @param headSha      commit the files were taken from, or null
 * @param filesAnalyzed number of files sent to the analysis
 * @param tokensUsed   Claude tokens used
 * @param costUsd      estimated cost, or null
 * @param demo         true when no Claude API key is configured
 */
public record ReportContext(
        String repoFullName,
        String headSha,
        int filesAnalyzed,
        int tokensUsed,
        BigDecimal costUsd,
        boolean demo) {

    public static ReportContext inline(int filesAnalyzed, int tokensUsed, boolean demo) {
        return new ReportContext(null, null, filesAnalyzed, tokensUsed, null, demo);
    }

    public boolean hasRepository() {
        return repoFullName != null && !repoFullName.isBlank()
                && headSha != null && !headSha.isBlank();
    }
}
