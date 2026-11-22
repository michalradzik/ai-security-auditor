package com.aisecaudit.api;

import com.aisecaudit.service.AuditOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        // Fixed test values, so tests never use secrets from a local .env file
        "app.local.client-api-key=test-api-key-12345",
        "app.webhook.secret=local-dev-webhook-secret",
        "app.admin.api-key=local-dev-admin-key",
        "app.claude.api-key=",
        "app.github.token=",
        "app.report.language=en",
        "app.semgrep.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WebhookControllerTest {

    private static final String SECRET = "local-dev-webhook-secret";
    private static final String VALID_PAYLOAD = """
            {
              "repoFullName": "owner/repo",
              "prNumber": 42,
              "baseSha": "abc",
              "headSha": "def",
              "clientApiKey": "test-api-key-12345",
              "changedFiles": ["src/main/java/Foo.java"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditOrchestrator orchestrator;

    @Test
    void rejectsMissingSecret() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized());
        verify(orchestrator, never()).runAudit(anyString(), any(), any(), any(), anyList(), anyString());
    }

    @Test
    void rejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/audit")
                        .header("X-Webhook-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsIncompletePayload() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/audit")
                        .header("X-Webhook-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoFullName\":\"owner/repo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsValidRequestAndStartsAudit() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/audit")
                        .header("X-Webhook-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isAccepted());

        verify(orchestrator).runAudit("owner/repo", 42, "abc", "def",
                List.of("src/main/java/Foo.java"), "test-api-key-12345");
    }
}
