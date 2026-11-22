package com.aisecaudit.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownHtmlTest {

    @Test
    void convertsReportConstructs() {
        String html = MarkdownHtml.toHtml("""
                ## Title
                **bold** and `code` and [link](https://github.com/a/b)
                | A | B |
                | --- | ---: |
                | x | 1 |
                - item
                <details><summary>Fix</summary>

                ```java
                if (a < b) {}
                ```

                </details>
                """);

        assertTrue(html.contains("<h2>Title</h2>"));
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<code>code</code>"));
        assertTrue(html.contains("<a href=\"https://github.com/a/b\" rel=\"noopener\">link</a>"));
        assertTrue(html.contains("<th>A</th>"));
        assertTrue(html.contains("<td class=\"num\">1</td>"));
        assertTrue(html.contains("<li>item</li>"));
        assertTrue(html.contains("<details><summary>Fix</summary>"));
        assertTrue(html.contains("<pre><code class=\"language-java\">if (a &lt; b) {}"));
    }

    @Test
    void doesNotAllowInjectedHtmlOrJavascriptLinks() {
        String html = MarkdownHtml.toHtml("""
                <script>alert(1)</script>
                <img src=x onerror=alert(1)>
                [click](javascript:alert(1))
                """);

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("href=\"javascript"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void keepsEntitiesWrittenByRendererWithoutDoubleEscaping() {
        String html = MarkdownHtml.toHtml("a &lt;b&gt; & c");

        assertTrue(html.contains("a &lt;b&gt; &amp; c"));
        assertFalse(html.contains("&amp;lt;"));
    }

    @Test
    void pageEscapesTitle() {
        String page = MarkdownHtml.toHtmlPage("text", "<x>");

        assertTrue(page.contains("<title>&lt;x&gt;</title>"));
    }
}
