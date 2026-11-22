package com.aisecaudit.api.dto;

import com.aisecaudit.domain.Audit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API view of an audit. Entities are not returned directly: that would expose
 * the client's API key and break on lazy-loaded relations.
 */
public record AuditResponse(
        Long id,
        String repoFullName,
        Integer prNumber,
        String headSha,
        Audit.Status status,
        String aiSummary,
        Integer tokensUsed,
        BigDecimal estimatedCostUsd,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<FindingResponse> findings) {

    public static AuditResponse from(Audit audit) {
        return new AuditResponse(
                audit.getId(),
                audit.getRepoFullName(),
                audit.getPrNumber(),
                audit.getHeadSha(),
                audit.getStatus(),
                audit.getAiSummary(),
                audit.getTokensUsed(),
                audit.getEstimatedCostUsd(),
                audit.getStartedAt(),
                audit.getCompletedAt(),
                audit.getFindings().stream().map(FindingResponse::from).toList());
    }
}
