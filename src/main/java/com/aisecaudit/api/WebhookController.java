package com.aisecaudit.api;

import com.aisecaudit.security.SecretComparator;
import com.aisecaudit.service.AuditOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Called by n8n after a GitHub pull request event.
 * Payload: { "repoFullName", "prNumber", "baseSha", "headSha", "clientApiKey", "changedFiles" }
 */
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final AuditOrchestrator orchestrator;
    private final String webhookSecret;

    public WebhookController(AuditOrchestrator orchestrator,
                             @Value("${app.webhook.secret:}") String webhookSecret) {
        this.orchestrator = orchestrator;
        this.webhookSecret = webhookSecret;
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("WEBHOOK_SECRET is not set: all webhook calls will be rejected");
        }
    }

    @PostMapping("/audit")
    public ResponseEntity<Map<String, Object>> triggerAudit(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody Map<String, Object> payload) {

        if (!SecretComparator.matches(webhookSecret, secret)) {
            log.warn("Rejected webhook call: missing or invalid secret");
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized",
                    "message", "Missing or invalid X-Webhook-Secret header"));
        }

        String repoFullName = getString(payload, "repoFullName");
        Integer prNumber = getInt(payload, "prNumber");
        String baseSha = getString(payload, "baseSha");
        String headSha = getString(payload, "headSha");
        String clientApiKey = getString(payload, "clientApiKey");
        List<String> changedFiles = getStringList(payload, "changedFiles");

        if (repoFullName == null || prNumber == null || headSha == null || clientApiKey == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Required fields: repoFullName, prNumber, headSha, clientApiKey"));
        }

        log.info("Audit request accepted: PR #{} in {}", prNumber, repoFullName);

        // Runs asynchronously: 202 returns immediately, the report is posted as a PR comment
        orchestrator.runAudit(repoFullName, prNumber, baseSha, headSha, changedFiles, clientApiKey);

        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Audit started, the report will be posted as a PR comment",
                "repo", repoFullName,
                "pr", prNumber));
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer i) {
            return i;
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> getStringList(Map<String, Object> map, String key) {
        if (map.get(key) instanceof List<?> list) {
            return list.stream().filter(item -> item != null).map(Object::toString).toList();
        }
        return List.of();
    }
}
