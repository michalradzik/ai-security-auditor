package com.aisecaudit.api.dto;

import com.aisecaudit.domain.Finding;

public record FindingResponse(
        Long id,
        String ruleId,
        Finding.Severity severity,
        String filePath,
        Integer lineNumber,
        String message,
        String aiExplanation,
        String suggestedFix,
        Boolean falsePositive) {

    public static FindingResponse from(Finding finding) {
        return new FindingResponse(
                finding.getId(),
                finding.getRuleId(),
                finding.getSeverity(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getMessage(),
                finding.getAiExplanation(),
                finding.getSuggestedFix(),
                finding.getIsFalsePositive());
    }
}
