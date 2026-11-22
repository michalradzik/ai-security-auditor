package com.aisecaudit.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportLinksTest {

    private final ReportLinks links = new ReportLinks("link-secret", "webhook-secret", "https://audit.example.com/");

    @Test
    void urlContainsIdAndValidToken() {
        String url = links.url(7);
        String token = url.substring(url.lastIndexOf('/') + 1);

        assertTrue(url.startsWith("https://audit.example.com/r/7/"));
        assertEquals(32, token.length());
        assertTrue(token.matches("[0-9a-f]+"));
        assertTrue(links.matches(7, token));
    }

    @Test
    void tokenOfOneAuditDoesNotOpenAnother() {
        assertFalse(links.matches(8, links.token(7)));
        assertFalse(links.matches(7, links.token(7) + "x"));
        assertFalse(links.matches(7, null));
    }

    @Test
    void differentSecretGivesDifferentToken() {
        ReportLinks other = new ReportLinks("another-secret", "", "http://localhost:8080");
        assertFalse(other.matches(7, links.token(7)));
    }

    @Test
    void fallsBackToWebhookSecretAndFailsClosedWithoutAny() {
        assertTrue(new ReportLinks("", "webhook-secret", "http://x").enabled());

        ReportLinks disabled = new ReportLinks("", "", "http://x");
        assertFalse(disabled.enabled());
        assertEquals(null, disabled.url(1));
        assertFalse(disabled.matches(1, "anything"));
    }
}
