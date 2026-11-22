package com.aisecaudit.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretComparatorTest {

    @Test
    void matchesIdenticalSecrets() {
        assertTrue(SecretComparator.matches("s3cret", "s3cret"));
    }

    @Test
    void rejectsDifferentSecrets() {
        assertFalse(SecretComparator.matches("s3cret", "s3cret2"));
        assertFalse(SecretComparator.matches("s3cret", "S3CRET"));
    }

    @Test
    void rejectsMissingProvidedValue() {
        assertFalse(SecretComparator.matches("s3cret", null));
        assertFalse(SecretComparator.matches("s3cret", ""));
    }

    @Test
    void failsClosedWhenExpectedSecretIsNotConfigured() {
        assertFalse(SecretComparator.matches(null, "anything"));
        assertFalse(SecretComparator.matches("", ""));
        assertFalse(SecretComparator.matches("   ", "   "));
    }
}
