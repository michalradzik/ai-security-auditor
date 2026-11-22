package com.aisecaudit.security;

import com.aisecaudit.domain.Client;
import com.aisecaudit.repository.ClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates API calls with a static API key sent in a header.
 * <ul>
 *   <li>{@code X-Admin-Api-Key}: grants ROLE_ADMIN (client management)</li>
 *   <li>{@code X-Client-Api-Key}: grants ROLE_CLIENT for an active client</li>
 * </ul>
 * Requests without a valid key stay anonymous; {@code SecurityConfig} decides what they may access.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String ADMIN_HEADER = "X-Admin-Api-Key";
    public static final String CLIENT_HEADER = "X-Client-Api-Key";

    private final ClientRepository clientRepository;
    private final String adminApiKey;

    public ApiKeyAuthFilter(ClientRepository clientRepository,
                            @Value("${app.admin.api-key:}") String adminApiKey) {
        this.clientRepository = clientRepository;
        this.adminApiKey = adminApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String adminKey = request.getHeader(ADMIN_HEADER);
        String clientKey = request.getHeader(CLIENT_HEADER);

        if (adminKey != null && SecretComparator.matches(adminApiKey, adminKey)) {
            authenticate("admin", "ROLE_ADMIN");
        } else if (clientKey != null && !clientKey.isBlank()) {
            Optional<Client> client = clientRepository.findByApiKey(clientKey);
            if (client.isPresent() && client.get().isActive()) {
                authenticate("client:" + client.get().getId(), "ROLE_CLIENT");
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String principal, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
