package com.aisecaudit.api;

import com.aisecaudit.api.dto.ClientResponse;
import com.aisecaudit.api.dto.CreatedClientResponse;
import com.aisecaudit.domain.Client;
import com.aisecaudit.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Client management. Requires the admin API key (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRepository clientRepository;

    public ClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<ClientResponse> getClients() {
        return clientRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    /** Body: { "name": "...", "email": "..." }. The API key is returned only in this response. */
    @PostMapping
    public ResponseEntity<CreatedClientResponse> createClient(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String apiKey = generateApiKey();
        Client client = Client.builder()
                .name(name)
                .email(body.get("email"))
                .apiKey(apiKey)
                .active(true)
                .build();
        client = clientRepository.save(client);

        log.info("Created client id={} name={}", client.getId(), client.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedClientResponse(ClientResponse.from(client), apiKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClient(@PathVariable("id") Long id) {
        return clientRepository.findById(id)
                .map(ClientResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ClientResponse> setActive(@PathVariable("id") Long id,
                                                    @RequestParam("active") boolean active) {
        return clientRepository.findById(id)
                .map(client -> {
                    client.setActive(active);
                    return ResponseEntity.ok(ClientResponse.from(clientRepository.save(client)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "asa_" + HexFormat.of().formatHex(bytes);
    }
}
