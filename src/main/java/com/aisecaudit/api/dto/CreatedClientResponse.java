package com.aisecaudit.api.dto;

/** Returned once, right after a client is created: the only time the API key is shown. */
public record CreatedClientResponse(ClientResponse client, String apiKey) {
}
