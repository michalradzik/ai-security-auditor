package com.aisecaudit.repository;

import com.aisecaudit.domain.Audit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditRepository
        extends JpaRepository<Audit, Long> {

    List<Audit> findByRepoFullNameOrderByStartedAtDesc(
        String repoFullName, Pageable pageable);

    Optional<Audit> findByRepoFullNameAndPrNumber(
        String repoFullName, Integer prNumber);

    long countByStatus(Audit.Status status);

    Optional<Audit> findTopByOrderByIdDesc();
}
