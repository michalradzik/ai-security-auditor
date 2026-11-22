package com.aisecaudit.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextLimitsTest {

    @Test
    void keepsShortText() {
        assertEquals("abc", TextLimits.truncate("abc", 10));
        assertEquals("abcde", TextLimits.truncate("abcde", 5));
    }

    @Test
    void cutsLongTextToExactLimit() {
        String result = TextLimits.truncate("a".repeat(513), 500);
        assertEquals(500, result.length());
        assertEquals('…', result.charAt(499));
    }

    @Test
    void handlesNull() {
        assertNull(TextLimits.truncate(null, 10));
    }
}
