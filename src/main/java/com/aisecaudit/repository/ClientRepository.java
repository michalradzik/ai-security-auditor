package com.aisecaudit.repository;

import com.aisecaudit.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository
        extends JpaRepository<Client, Long> {

    Optional<Client> findByApiKey(String apiKey);

    boolean existsByApiKey(String apiKey);
}
