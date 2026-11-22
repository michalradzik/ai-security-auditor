package com.aisecaudit.report;

import com.aisecaudit.security.SecretComparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Signed, shareable report links: {@code /r/{id}/{token}}.
 * The token is an HMAC of the audit id, so a link opens exactly one report
 * and cannot be guessed or changed to another id. No API key header is needed,
 * which makes the link clickable from Telegram, Slack or e-mail.
 */
@Component
public class ReportLinks {

    /** 32 hex characters = 128 bits of the HMAC. */
    private static final int TOKEN_LENGTH = 32;

    private final byte[] secret;
    private final String baseUrl;

    public ReportLinks(@Value("${app.report.link-secret:}") String linkSecret,
                       @Value("${app.webhook.secret:}") String webhookSecret,
                       @Value("${app.public-base-url:http://localhost:8080}") String baseUrl) {
        String key = linkSecret != null && !linkSecret.isBlank() ? linkSecret : webhookSecret;
        this.secret = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** False when no secret is configured: links are then disabled (fail closed). */
    public boolean enabled() {
        return secret.length > 0;
    }

    /** Public URL of the report, or null when links are disabled. */
    public String url(long auditId) {
        return enabled() ? baseUrl + "/r/" + auditId + "/" + token(auditId) : null;
    }

    public boolean matches(long auditId, String token) {
        return enabled() && token != null && SecretComparator.matches(token(auditId), token);
    }

    String token(long auditId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(("audit-report:" + auditId).getBytes(StandardCharsets.UTF_8));
            // Hex only: no '_' or '-' that chat apps (Telegram Markdown) could treat as formatting
            return HexFormat.of().formatHex(digest).substring(0, TOKEN_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
