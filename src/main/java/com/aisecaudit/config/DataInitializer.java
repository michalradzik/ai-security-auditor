package com.aisecaudit.config;

import com.aisecaudit.domain.Client;
import com.aisecaudit.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Creates a client in the local profile so the API can be tried without setup.
 * The key comes from DEFAULT_CLIENT_API_KEY (same value n8n sends),
 * falling back to a development-only key.
 */
@Configuration
public class DataInitializer {

    /** Development-only fallback, valid only with the in-memory H2 database of the local profile. */
    public static final String LOCAL_TEST_API_KEY = "test-api-key-12345";

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Profile("local")
    public CommandLineRunner initTestData(ClientRepository clientRepository,
                                          @Value("${app.local.client-api-key:" + LOCAL_TEST_API_KEY + "}")
                                          String configuredKey) {
        // An empty DEFAULT_CLIENT_API_KEY= line in .env counts as "not set"
        String clientApiKey = configuredKey == null || configuredKey.isBlank()
                ? LOCAL_TEST_API_KEY
                : configuredKey.trim();
        return args -> {
            if (clientRepository.existsByApiKey(clientApiKey)) {
                return;
            }
            clientRepository.save(Client.builder()
                    .name("Local test client")
                    .email("test@aisecaudit.local")
                    .apiKey(clientApiKey)
                    .active(true)
                    .monthlyAuditLimit(1000)
                    .build());
            if (LOCAL_TEST_API_KEY.equals(clientApiKey)) {
                log.info("Local test client created. Use header X-Client-Api-Key: {}", LOCAL_TEST_API_KEY);
            } else {
                log.info("Local test client created with the key from DEFAULT_CLIENT_API_KEY");
            }
        };
    }
}
