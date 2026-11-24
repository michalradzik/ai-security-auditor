package com.aisecaudit.service;

import com.aisecaudit.domain.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges Semgrep and AI findings into one list:
 * <ul>
 *   <li>Semgrep rules that the AI reported as false positives are marked {@code isFalsePositive}.</li>
 *   <li>An AI finding in the same file and (almost) the same line as a Semgrep finding is folded into it:
 *       the Semgrep entry keeps its rule id and gets the AI explanation, fix and the higher severity.</li>
 *   <li>Other AI findings are added as they are.</li>
 * </ul>
 */
@Component
public class FindingMerger {

    /** The model sometimes points one or two lines away from Semgrep for the same problem. */
    static final int LINE_TOLERANCE = 2;

    public List<Finding> merge(List<Finding> semgrepFindings, List<Finding> aiFindings, List<String> falsePositives) {
        List<Finding> merged = new ArrayList<>(semgrepFindings);
        for (Finding semgrep : semgrepFindings) {
            if (isReportedAsFalsePositive(semgrep, falsePositives)) {
                semgrep.setIsFalsePositive(true);
            }
        }
        for (Finding ai : aiFindings) {
            Finding match = closestMatch(ai, semgrepFindings);
            if (match == null) {
                merged.add(ai);
            } else {
                foldInto(match, ai);
            }
        }
        return merged;
    }

    private static boolean isReportedAsFalsePositive(Finding semgrep, List<String> falsePositives) {
        String ruleId = semgrep.getRuleId();
        if (ruleId == null || ruleId.isBlank() || falsePositives == null) {
            return false;
        }
        for (String entry : falsePositives) {
            if (entry != null && entry.contains(ruleId)) {
                return true;
            }
        }
        return false;
    }

    /** Semgrep finding in the same file whose line is closest to the AI line, within the tolerance. */
    private static Finding closestMatch(Finding ai, List<Finding> semgrepFindings) {
        if (ai.getFilePath() == null || ai.getLineNumber() == null) {
            return null;
        }
        Finding best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Finding semgrep : semgrepFindings) {
            if (Boolean.TRUE.equals(semgrep.getIsFalsePositive())
                    || semgrep.getLineNumber() == null
                    || !ai.getFilePath().equals(semgrep.getFilePath())) {
                continue;
            }
            int distance = Math.abs(ai.getLineNumber() - semgrep.getLineNumber());
            if (distance <= LINE_TOLERANCE && distance < bestDistance) {
                best = semgrep;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void foldInto(Finding semgrep, Finding ai) {
        semgrep.setAiExplanation(ai.getAiExplanation());
        semgrep.setSuggestedFix(ai.getSuggestedFix());
        semgrep.setSeverity(moreSevere(semgrep.getSeverity(), ai.getSeverity()));
    }

    /** Enum order is CRITICAL, HIGH, MEDIUM, LOW, INFO: a lower ordinal is more severe. */
    static Finding.Severity moreSevere(Finding.Severity a, Finding.Severity b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
