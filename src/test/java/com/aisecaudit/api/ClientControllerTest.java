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

import static org.hamcrest.Matchers.startsWith;
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
class ClientControllerTest {

    private static final String ADMIN_KEY = "local-dev-admin-key";
    private static final String CLIENT_KEY = "test-api-key-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditOrchestrator orchestrator;

    @Test
    void clientManagementRequiresAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clients").header("X-Client-Api-Key", CLIENT_KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createdClientGetsKeyOnceAndListHidesIt() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"email\":\"dev@acme.io\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.name").value("Acme"))
                .andExpect(jsonPath("$.apiKey", startsWith("asa_")));

        mockMvc.perform(get("/api/v1/clients").header("X-Admin-Api-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[*].apiKey").isEmpty());
    }

    @Test
    void createClientRequiresName() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@acme.io\"}"))
                .andExpect(status().isBadRequest());
    }
}
