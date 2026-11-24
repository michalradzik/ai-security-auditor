package com.aisecaudit.service;

import com.aisecaudit.domain.Audit;
import com.aisecaudit.domain.Client;
import com.aisecaudit.domain.Finding;
import com.aisecaudit.report.AiFinding;
import com.aisecaudit.report.ReportContext;
import com.aisecaudit.report.ReportLinks;
import com.aisecaudit.report.ReportRenderer;
import com.aisecaudit.repository.AuditRepository;
import com.aisecaudit.repository.ClientRepository;
import com.aisecaudit.util.TextLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Runs an audit: fetch files, Semgrep, Claude, post the report, store the results. */
@Service
public class AuditOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AuditOrchestrator.class);

    /** Estimated blended price per million tokens (input + output) used for the cost column. */
    private static final BigDecimal USD_PER_MILLION_TOKENS = new BigDecimal("9.00");

    private final SemgrepService semgrepService;
    private final ClaudeService claudeService;
    private final GitHubService githubService;
    private final ReportRenderer reportRenderer;
    private final ReportLinks reportLinks;
    private final FindingMerger findingMerger;
    private final AuditRepository auditRepository;
    private final ClientRepository clientRepository;

    public AuditOrchestrator(SemgrepService semgrepService,
                             ClaudeService claudeService,
                             GitHubService githubService,
                             ReportRenderer reportRenderer,
                             ReportLinks reportLinks,
                             FindingMerger findingMerger,
                             AuditRepository auditRepository,
                             ClientRepository clientRepository) {
        this.semgrepService = semgrepService;
        this.claudeService = claudeService;
        this.githubService = githubService;
        this.reportRenderer = reportRenderer;
        this.reportLinks = reportLinks;
        this.findingMerger = findingMerger;
        this.auditRepository = auditRepository;
        this.clientRepository = clientRepository;
    }

    @Async("auditTaskExecutor")
    public CompletableFuture<Audit> runAudit(String repoFullName,
                                             Integer prNumber,
                                             String baseSha,
                                             String headSha,
                                             List<String> changedFiles,
                                             String clientApiKey) {

        log.info("▶ AUDIT START | PR #{} | {}", prNumber, repoFullName);

        Client client = clientRepository.findByApiKey(clientApiKey).orElse(null);
        if (client == null || !client.isActive()) {
            log.error("Unknown or inactive client (API key does not match an active client)");
            return CompletableFuture.completedFuture(null);
        }

        final Audit savedAudit = auditRepository.save(Audit.builder()
                .client(client)
                .repoFullName(repoFullName)
                .prNumber(prNumber)
                .baseSha(baseSha)
                .headSha(headSha)
                .status(Audit.Status.RUNNING)
                .build());

        try {
            log.info("  [1/4] Fetching files from GitHub");
            Map<String, String> files =
                    githubService.getChangedFilesContent(repoFullName, baseSha, headSha, changedFiles);
            if (files.isEmpty()) {
                log.warn("  No Java/Kotlin files to analyze in PR #{}", prNumber);
                return CompletableFuture.completedFuture(failAudit(savedAudit, "No Java/Kotlin files"));
            }

            log.info("  [2/4] Semgrep ({} files)", files.size());
            List<Finding> semgrepFindings = semgrepService.analyze(files);
            semgrepFindings.forEach(f -> f.setAudit(savedAudit));

            log.info("  [3/4] Claude analysis");
            ClaudeService.AnalysisResult aiResult = claudeService.analyze(files, semgrepFindings);
            BigDecimal cost = calculateCost(aiResult.tokensUsed());

            // Re-render with repository context: links to files and lines, commit, cost
            String report = aiResult.report() == null
                    ? aiResult.markdownReport()
                    : reportRenderer.render(aiResult.report(), new ReportContext(
                    repoFullName, headSha, files.size(), aiResult.tokensUsed(), cost, aiResult.isDemo()));

            log.info("  [4/4] Publishing the report to PR #{}", prNumber);
            if (prNumber != null && prNumber > 0) {
                githubService.postPrComment(repoFullName, prNumber, report);
            } else {
                log.info("  No pull request number: report saved, not posted to GitHub");
            }

            List<Finding> aiFindings = new ArrayList<>();
            List<String> falsePositives = List.of();
            if (aiResult.report() != null && !aiResult.isDemo()) {
                aiResult.report().findings().forEach(f -> aiFindings.add(toEntity(f, savedAudit)));
                falsePositives = aiResult.report().falsePositives();
            }
            // one entry per problem: AI findings on Semgrep lines are folded in, false positives are marked
            List<Finding> allFindings = findingMerger.merge(semgrepFindings, aiFindings, falsePositives);

            savedAudit.setStatus(Audit.Status.COMPLETED);
            savedAudit.setFindings(allFindings);
            savedAudit.setAiSummary(TextLimits.truncate(aiResult.summary(), 1000));
            savedAudit.setMarkdownReport(report);
            savedAudit.setCompletedAt(LocalDateTime.now());
            savedAudit.setTokensUsed(aiResult.tokensUsed());
            savedAudit.setEstimatedCostUsd(cost);
            Audit completedAudit = auditRepository.save(savedAudit);

            log.info("✅ AUDIT DONE | PR #{} | {} findings | {} tokens | ${}",
                    prNumber, allFindings.size(), aiResult.tokensUsed(), cost);
            if (reportLinks.enabled()) {
                log.info("   Report: {}", reportLinks.url(completedAudit.getId()));
            }
            return CompletableFuture.completedFuture(completedAudit);

        } catch (Exception e) {
            log.error("❌ AUDIT FAILED | PR #{}: {}", prNumber, e.getMessage(), e);
            return CompletableFuture.completedFuture(failAudit(savedAudit, e.getMessage()));
        }
    }

    /** Audits inline code without GitHub (used by POST /api/v1/audits/test). */
    public ClaudeService.AnalysisResult testAudit(Map<String, String> filesContent) {
        log.info("Test audit: {} file(s)", filesContent.size());
        List<Finding> semgrepFindings = semgrepService.analyze(filesContent);
        return claudeService.analyze(filesContent, semgrepFindings);
    }

    private static Finding toEntity(AiFinding finding, Audit audit) {
        return Finding.builder()
                .audit(audit)
                .ruleId(TextLimits.truncate("ai:" + finding.title(), 255))
                .message(finding.title())
                .severity(Finding.Severity.valueOf(finding.severity().name()))
                .filePath(finding.file())
                .lineNumber(finding.line())
                .aiExplanation(finding.risk())
                .suggestedFix(finding.fix())
                .build();
    }

    private Audit failAudit(Audit audit, String reason) {
        audit.setStatus(Audit.Status.FAILED);
        audit.setCompletedAt(LocalDateTime.now());
        audit.setAiSummary(TextLimits.truncate("Error: " + (reason != null ? reason : "unknown"), 1000));
        return auditRepository.save(audit);
    }

    private BigDecimal calculateCost(int tokens) {
        if (tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return USD_PER_MILLION_TOKENS
                .multiply(new BigDecimal(tokens))
                .divide(new BigDecimal("1000000"), 6, RoundingMode.HALF_UP);
    }
}
