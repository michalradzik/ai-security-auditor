package com.aisecaudit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs at startup which secrets are configured, without ever printing their values.
 * Makes "the key is not loaded" problems visible immediately.
 */
@Component
public class ConfigurationReport {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationReport.class);

    private final String claudeApiKey;
    private final String githubToken;
    private final String webhookSecret;
    private final String adminApiKey;

    public ConfigurationReport(@Value("${app.claude.api-key:}") String claudeApiKey,
                               @Value("${app.github.token:}") String githubToken,
                               @Value("${app.webhook.secret:}") String webhookSecret,
                               @Value("${app.admin.api-key:}") String adminApiKey) {
        this.claudeApiKey = claudeApiKey;
        this.githubToken = githubToken;
        this.webhookSecret = webhookSecret;
        this.adminApiKey = adminApiKey;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        log.info("Configuration: CLAUDE_API_KEY {}, GITHUB_TOKEN {}, WEBHOOK_SECRET {}, ADMIN_API_KEY {}",
                describe(claudeApiKey), describe(githubToken), describe(webhookSecret), describe(adminApiKey));
    }

    /** "missing" or "set (N chars, starts with abcd)": enough to spot a wrong or stale value. */
    static String describe(String value) {
        if (value == null || value.isBlank()) {
            return "missing";
        }
        String trimmed = value.trim();
        String prefix = trimmed.length() > 8 ? trimmed.substring(0, 4) : "****";
        String warning = trimmed.length() != value.length() ? ", WARNING: leading/trailing spaces" : "";
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            warning += ", WARNING: value is wrapped in quotes";
        }
        return "set (" + trimmed.length() + " chars, starts with " + prefix + warning + ")";
    }
}
