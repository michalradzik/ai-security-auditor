package com.aisecaudit.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time comparison of secrets (API keys, webhook secrets).
 * {@code String.equals} returns early on the first different character,
 * which leaks timing information to an attacker.
 */
public final class SecretComparator {

    private SecretComparator() {
    }

    /**
     * Returns true only if both values are non-blank and identical.
     * A blank expected value means "not configured" and never matches (fail closed).
     */
    public static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
