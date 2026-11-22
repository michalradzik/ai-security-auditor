package com.aisecaudit.api;

import com.aisecaudit.service.AuditOrchestrator;
import com.aisecaudit.service.ClaudeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class AuditControllerTest {

    private static final String CLIENT_KEY = "test-api-key-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditOrchestrator orchestrator;

    @Test
    void statsRequireApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/audits/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statsWithClientKey() throws Exception {
        mockMvc.perform(get("/api/v1/audits/stats").header("X-Client-Api-Key", CLIENT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void testAuditReturnsReport() throws Exception {
        when(orchestrator.testAudit(anyMap()))
                .thenReturn(new ClaudeService.AnalysisResult("## Test Report\nNo issues.", "No issues.", 100));

        String body = """
                {
                  "filename": "TestController.java",
                  "code": "@RestController public class TestController { }"
                }
                """;

        mockMvc.perform(post("/api/v1/audits/test")
                        .header("X-Client-Api-Key", CLIENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report").value("## Test Report\nNo issues."))
                .andExpect(jsonPath("$.tokensUsed").value(100));
    }

    @Test
    void testAuditRejectsMissingCode() throws Exception {
        mockMvc.perform(post("/api/v1/audits/test")
                        .header("X-Client-Api-Key", CLIENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"A.java\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAuditReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/audits/999999").header("X-Client-Api-Key", CLIENT_KEY))
                .andExpect(status().isNotFound());
    }
}
