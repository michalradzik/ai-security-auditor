package com.aisecaudit.api.dto;

import com.aisecaudit.domain.Client;

import java.time.LocalDateTime;

/** Public view of a client. Never contains the API key. */
public record ClientResponse(
        Long id,
        String name,
        String email,
        boolean active,
        Integer monthlyAuditLimit,
        Integer monthlyAuditsUsed,
        LocalDateTime createdAt) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.isActive(),
                client.getMonthlyAuditLimit(),
                client.getMonthlyAuditsUsed(),
                client.getCreatedAt());
    }
}
