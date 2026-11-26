package com.aisecaudit.api;

import com.aisecaudit.domain.Audit;
import com.aisecaudit.report.MarkdownHtml;
import com.aisecaudit.report.ReportLinks;
import com.aisecaudit.repository.AuditRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Opens a report from a signed link, without an API key. Invalid links get 404, not 401. */
@RestController
public class ReportLinkController {

    /**
     * The page needs only its own inline CSS: no scripts, images, fonts, frames or forms.
     * Reports contain code from pull requests, so even if HTML escaping ever failed,
     * the browser would refuse to run any injected script.
     */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private final AuditRepository auditRepository;
    private final ReportLinks reportLinks;

    public ReportLinkController(AuditRepository auditRepository, ReportLinks reportLinks) {
        this.auditRepository = auditRepository;
        this.reportLinks = reportLinks;
    }

    @GetMapping(value = "/r/{id}/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> openReport(@PathVariable("id") long id, @PathVariable("token") String token) {
        if (!reportLinks.matches(id, token)) {
            return page(HttpStatus.NOT_FOUND, MarkdownHtml.toHtmlPage("## Report not found", "Not found"));
        }
        return auditRepository.findById(id)
                .map(this::render)
                .orElseGet(() -> page(HttpStatus.NOT_FOUND, MarkdownHtml.toHtmlPage("## Report not found", "Not found")));
    }

    private ResponseEntity<String> render(Audit audit) {
        String title = "Audit #" + audit.getId() + " · " + audit.getRepoFullName();
        String report = audit.getMarkdownReport();
        if (report != null && !report.isBlank()) {
            return page(HttpStatus.OK, MarkdownHtml.toHtmlPage(report, title));
        }
        String message = audit.getStatus() == Audit.Status.RUNNING
                ? "## ⏳ Audit in progress\n\nThe report is not ready yet. Refresh this page in a minute."
                : "## ❌ Audit failed\n\n" + (audit.getAiSummary() != null ? audit.getAiSummary() : "No report.");
        return page(HttpStatus.OK, MarkdownHtml.toHtmlPage(message, title));
    }

    private static ResponseEntity<String> page(HttpStatus status, String html) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
