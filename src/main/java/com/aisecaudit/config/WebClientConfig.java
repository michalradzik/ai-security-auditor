package com.aisecaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean(name = "claudeWebClient")
    public WebClient claudeWebClient() {
        return WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("anthropic-version", "2023-06-01")
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    @Bean(name = "githubWebClient")
    public WebClient githubWebClient() {
        return WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept",
                "application/vnd.github.v3+json")
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024))
            .build();
    }
}
