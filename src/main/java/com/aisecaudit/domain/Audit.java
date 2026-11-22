package com.aisecaudit.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.aisecaudit.util.TextLimits;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "audits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "findings")
@ToString(exclude = "findings")
public class Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "base_sha")
    private String baseSha;

    @Column(name = "head_sha")
    private String headSha;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @OneToMany(
        mappedBy = "audit",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Finding> findings = new ArrayList<>();

    @Column(name = "ai_summary", length = 1000)
    private String aiSummary;

    @Column(name = "markdown_report",
            columnDefinition = "TEXT")
    private String markdownReport;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "estimated_cost_usd",
            precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd;

    /** AI output length is not predictable: never let it break the save. */
    @PrePersist
    @PreUpdate
    void limitTextLengths() {
        aiSummary = TextLimits.truncate(aiSummary, 1000);
        repoFullName = TextLimits.truncate(repoFullName, 255);
    }

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
