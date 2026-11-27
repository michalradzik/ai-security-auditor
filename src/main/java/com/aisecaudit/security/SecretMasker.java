package com.aisecaudit.security;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks hardcoded secret values in scanned code before it is stored or sent to the AI model.
 * <p>
 * The finding still shows that a secret is hardcoded (and where), but the value itself never
 * reaches the database, the API response or a third-party LLM. Only characters on a line are
 * replaced, never line breaks, so line numbers in the report stay correct.
 */
public final class SecretMasker {

    static final String MASK = "****";

    /** {@code password = "value"}, {@code apiKey: 'value'}, {@code DB_PASSWORD="value"} ... */
    private static final Pattern QUOTED_ASSIGNMENT = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)"
                    + "[\\w.-]*[\"']?\\s*[:=]\\s*[\"'])(?!\\$\\{)([^\"'\\s]{4,})([\"'])");

    /** {@code password=value} in .properties / .env, {@code secret: value} in YAML. */
    private static final Pattern UNQUOTED_ASSIGNMENT = Pattern.compile(
            "(?im)^(\\s*[\\w.-]*(?:password|passwd|secret|token|api[_-]?key|access[_-]?key)[\\w.-]*\\s*[:=]\\s*)"
                    + "(?![\"'$])([^\\s\"'#]{4,})\\s*$");

    /** Well-known key formats that are secret wherever they appear. */
    private static final List<Pattern> KNOWN_KEYS = List.of(
            Pattern.compile("\\b((?:AKIA|ASIA))[0-9A-Z]{16}\\b"),          // AWS access key id
            Pattern.compile("\\b(gh[pousr]_)[A-Za-z0-9]{36,}\\b"),         // GitHub token
            Pattern.compile("\\b(sk-ant-)[A-Za-z0-9_-]{20,}"),             // Anthropic key
            Pattern.compile("\\b(sk-)(?!ant-)[A-Za-z0-9_-]{20,}"));        // OpenAI-style key

    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(-----BEGIN [A-Z ]*PRIVATE KEY-----)(.*?)(-----END [A-Z ]*PRIVATE KEY-----)", Pattern.DOTALL);

    private SecretMasker() {
    }

    public static String mask(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        String result = maskValues(QUOTED_ASSIGNMENT, code, true);
        result = maskValues(UNQUOTED_ASSIGNMENT, result, false);
        for (Pattern key : KNOWN_KEYS) {
            result = key.matcher(result).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + MASK));
        }
        return PRIVATE_KEY_BLOCK.matcher(result).replaceAll(m -> Matcher.quoteReplacement(
                m.group(1) + m.group(2).replaceAll("[^\\r\\n]+", MASK) + m.group(3)));
    }

    /** Keeps the first two characters so the reader still sees a literal was there. */
    private static String maskValues(Pattern pattern, String code, boolean hasClosingQuote) {
        return pattern.matcher(code).replaceAll(m -> {
            String value = m.group(2);
            String masked = m.group(1) + value.substring(0, 2) + MASK + (hasClosingQuote ? m.group(3) : "");
            return Matcher.quoteReplacement(masked);
        });
    }
}
