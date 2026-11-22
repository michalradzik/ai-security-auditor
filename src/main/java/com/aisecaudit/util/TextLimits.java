package com.aisecaudit.util;

/** Keeps text within database column limits. AI output length is not predictable. */
public final class TextLimits {

    private static final String ELLIPSIS = "…";

    private TextLimits() {
    }

    /** Returns the text unchanged if it fits, otherwise cuts it and ends it with an ellipsis. */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= ELLIPSIS.length()) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - ELLIPSIS.length()) + ELLIPSIS;
    }
}
