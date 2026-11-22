package com.aisecaudit.domain;

import jakarta.persistence.*;
import lombok.*;
import com.aisecaudit.util.TextLimits;

@Entity
@Table(name = "findings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "audit")
@ToString(exclude = "audit")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id")
    private Audit audit;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "code_snippet", length = 500)
    private String codeSnippet;

    @Column(name = "is_false_positive")
    @Builder.Default
    private Boolean isFalsePositive = false;

    @Column(name = "ai_explanation", length = 2000)
    private String aiExplanation;

    @Column(name = "suggested_fix", length = 2000)
    private String suggestedFix;

    /** Semgrep and AI output can be longer than the columns: cut instead of failing. */
    @PrePersist
    @PreUpdate
    void limitTextLengths() {
        ruleId = TextLimits.truncate(ruleId, 255);
        message = TextLimits.truncate(message, 1000);
        filePath = TextLimits.truncate(filePath, 255);
        codeSnippet = TextLimits.truncate(codeSnippet, 500);
        aiExplanation = TextLimits.truncate(aiExplanation, 2000);
        suggestedFix = TextLimits.truncate(suggestedFix, 2000);
    }

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO
    }
}
