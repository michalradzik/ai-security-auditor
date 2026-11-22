package com.aisecaudit.api;

import com.aisecaudit.api.dto.AuditResponse;
import com.aisecaudit.domain.Audit;
import com.aisecaudit.report.MarkdownHtml;
import com.aisecaudit.report.ReportLinks;
import com.aisecaudit.repository.AuditRepository;
import com.aisecaudit.service.AuditOrchestrator;
import com.aisecaudit.service.ClaudeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);
    private static final int MAX_LIMIT = 100;
    private static final int MAX_CODE_LENGTH = 50_000;

    private final AuditRepository auditRepository;
    private final AuditOrchestrator orchestrator;
    private final ReportLinks reportLinks;

    public AuditController(AuditRepository auditRepository, AuditOrchestrator orchestrator, ReportLinks reportLinks) {
        this.auditRepository = auditRepository;
        this.orchestrator = orchestrator;
        this.reportLinks = reportLinks;
    }

    @GetMapping(value = "/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getReportHtml(@PathVariable("id") Long id) {
        return auditRepository.findById(id)
                .filter(audit -> audit.getMarkdownReport() != null && !audit.getMarkdownReport().isBlank())
                .map(audit -> ResponseEntity.ok(MarkdownHtml.toHtmlPage(
                        audit.getMarkdownReport(),
                        "Audit #" + audit.getId() + " · " + audit.getRepoFullName())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AuditResponse> getAudit(@PathVariable("id") Long id) {
        return auditRepository.findById(id)
                .map(AuditResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditResponse> getAudits(@RequestParam("repo") String repo,
                                         @RequestParam(value = "limit", defaultValue = "10") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return auditRepository
                .findByRepoFullNameOrderByStartedAtDesc(repo, PageRequest.of(0, safeLimit))
                .stream()
                .map(AuditResponse::from)
                .toList();
    }

    /** Signed link to the report that opens without an API key (e.g. from Telegram). */
    @GetMapping("/{id}/link")
    public ResponseEntity<Map<String, Object>> getReportLink(@PathVariable("id") Long id) {
        if (!auditRepository.existsById(id) || !reportLinks.enabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("auditId", id, "reportUrl", reportLinks.url(id)));
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        long total = auditRepository.count();
        long completed = auditRepository.countByStatus(Audit.Status.COMPLETED);
        long failed = auditRepository.countByStatus(Audit.Status.FAILED);
        long running = auditRepository.countByStatus(Audit.Status.RUNNING);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("running", running);
        stats.put("successRate", total > 0
                ? String.format(java.util.Locale.ROOT, "%.1f%%", completed * 100.0 / total)
                : "N/A");
        auditRepository.findTopByOrderByIdDesc().ifPresent(latest -> {
            stats.put("latestAuditId", latest.getId());
            stats.put("latestStatus", latest.getStatus());
            if (reportLinks.enabled()) {
                stats.put("latestReportUrl", reportLinks.url(latest.getId()));
            }
        });
        return stats;
    }

    /**
     * Audits inline code without GitHub (handy for local testing).
     * Body: { "filename": "UserController.java", "code": "..." }
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testAudit(@RequestBody Map<String, String> body) {
        String filename = body.getOrDefault("filename", "TestFile.java");
        String code = body.get("code");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Field 'code' is required"));
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Code is too long (max " + MAX_CODE_LENGTH + " characters)"));
        }

        log.info("Test audit: {}", filename);
        ClaudeService.AnalysisResult result = orchestrator.testAudit(Map.of(filename, code));

        return ResponseEntity.ok(Map.of(
                "file", filename,
                "tokensUsed", result.tokensUsed(),
                "report", result.markdownReport()));
    }

}
