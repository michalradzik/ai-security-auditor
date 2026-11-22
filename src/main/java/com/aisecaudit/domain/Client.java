package com.aisecaudit.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_key", unique = true,
            nullable = false)
    private String apiKey;

    private String email;

    @Builder.Default
    private boolean active = true;

    @Column(name = "monthly_audit_limit")
    @Builder.Default
    private Integer monthlyAuditLimit = 100;

    @Column(name = "monthly_audits_used")
    @Builder.Default
    private Integer monthlyAuditsUsed = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
