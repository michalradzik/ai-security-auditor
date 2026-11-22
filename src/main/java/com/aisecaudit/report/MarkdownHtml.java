package com.aisecaudit.report;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Markdown to HTML converter for the report subset produced by {@link ReportRenderer}:
 * headings, paragraphs, bold, inline code, links, tables, lists, code blocks, quotes,
 * {@code <details>} blocks and the {@code <sub>} footer.
 * Everything is HTML-escaped first; only those constructs are turned back into tags,
 * so text coming from the AI cannot inject markup into the page.
 */
public final class MarkdownHtml {

    private static final Pattern DETAILS_OPEN = Pattern.compile("^&lt;details&gt;&lt;summary&gt;(.*)&lt;/summary&gt;$");
    private static final Pattern SUB = Pattern.compile("^&lt;sub&gt;(.*)&lt;/sub&gt;$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern ORDERED = Pattern.compile("^\\d+\\. (.*)$");

    private MarkdownHtml() {
    }

    public static String toHtmlPage(String markdown, String title) {
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                body{font-family:-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;max-width:880px;margin:32px auto;padding:0 16px;color:#1f2328;line-height:1.55;background:#fff}
                h2{border-bottom:1px solid #d0d7de;padding-bottom:8px}
                h3{margin-top:28px}
                h4{margin:24px 0 6px}
                table{border-collapse:collapse;margin:12px 0}
                th,td{border:1px solid #d0d7de;padding:6px 14px}
                th{background:#f6f8fa;text-align:left}
                td.num{text-align:right}
                code{background:#eff1f3;border-radius:4px;padding:1px 5px;font-size:90%%}
                pre{background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;padding:12px;overflow:auto}
                pre code{background:none;padding:0}
                blockquote{margin:0;padding:8px 14px;border-left:4px solid #d4a72c;background:#fff8c5}
                details{border:1px solid #d0d7de;border-radius:6px;padding:6px 12px;margin:8px 0}
                summary{cursor:pointer;font-weight:600}
                hr{border:0;border-top:1px solid #d0d7de;margin:28px 0 8px}
                sub{color:#59636e}
                a{color:#0969da}
                </style></head><body>
                %s
                </body></html>
                """.formatted(escape(title), toHtml(markdown));
    }

    public static String toHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        List<String> tableRows = new ArrayList<>();
        String listTag = null;
        boolean inCode = false;
        String fence = null;

        for (String rawLine : markdown.split("\\R", -1)) {
            String trimmed = rawLine.trim();

            if (inCode) {
                if (trimmed.equals(fence)) {
                    html.append("</code></pre>\n");
                    inCode = false;
                } else {
                    html.append(escape(rawLine)).append('\n');
                }
                continue;
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                listTag = closeList(html, listTag);
                flushTable(html, tableRows);
                fence = trimmed.substring(0, 3);
                String lang = trimmed.substring(3).replaceAll("[^A-Za-z0-9+-]", "");
                html.append("<pre><code").append(lang.isEmpty() ? "" : " class=\"language-" + lang + "\"").append('>');
                inCode = true;
                continue;
            }

            String line = escape(trimmed);
            if (line.startsWith("|")) {
                listTag = closeList(html, listTag);
                tableRows.add(line);
                continue;
            }
            flushTable(html, tableRows);

            Matcher ordered = ORDERED.matcher(line);
            if (line.startsWith("- ") || ordered.matches()) {
                String tag = line.startsWith("- ") ? "ul" : "ol";
                if (!tag.equals(listTag)) {
                    listTag = closeList(html, listTag);
                    html.append('<').append(tag).append(">\n");
                    listTag = tag;
                }
                String item = tag.equals("ul") ? line.substring(2) : ordered.group(1);
                html.append("<li>").append(inline(item)).append("</li>\n");
                continue;
            }
            listTag = closeList(html, listTag);

            Matcher details = DETAILS_OPEN.matcher(line);
            Matcher sub = SUB.matcher(line);
            if (line.isEmpty()) {
                continue;
            } else if (details.matches()) {
                html.append("<details><summary>").append(inline(details.group(1))).append("</summary>\n");
            } else if (line.equals("&lt;/details&gt;")) {
                html.append("</details>\n");
            } else if (sub.matches()) {
                html.append("<p><sub>").append(inline(sub.group(1))).append("</sub></p>\n");
            } else if (line.equals("---")) {
                html.append("<hr>\n");
            } else if (line.startsWith("#### ")) {
                html.append("<h4>").append(inline(line.substring(5))).append("</h4>\n");
            } else if (line.startsWith("### ")) {
                html.append("<h3>").append(inline(line.substring(4))).append("</h3>\n");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(inline(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("# ")) {
                html.append("<h1>").append(inline(line.substring(2))).append("</h1>\n");
            } else if (line.startsWith("&gt; ")) {
                html.append("<blockquote>").append(inline(line.substring(5))).append("</blockquote>\n");
            } else {
                html.append("<p>").append(inline(line)).append("</p>\n");
            }
        }
        if (inCode) {
            html.append("</code></pre>\n");
        }
        closeList(html, listTag);
        flushTable(html, tableRows);
        return html.toString();
    }

    private static String closeList(StringBuilder html, String listTag) {
        if (listTag != null) {
            html.append("</").append(listTag).append(">\n");
        }
        return null;
    }

    private static void flushTable(StringBuilder html, List<String> rows) {
        if (rows.isEmpty()) {
            return;
        }
        html.append("<table>\n");
        boolean header = true;
        for (String row : rows) {
            String[] cells = row.replaceAll("^\\||\\|$", "").split("\\|", -1);
            if (cells.length > 0 && cells[0].trim().matches(":?-{3,}:?")) {
                continue;
            }
            html.append("<tr>");
            for (String cell : cells) {
                String value = cell.trim();
                if (header) {
                    html.append("<th>").append(inline(value)).append("</th>");
                } else {
                    boolean number = value.matches("\\d+");
                    html.append(number ? "<td class=\"num\">" : "<td>").append(inline(value)).append("</td>");
                }
            }
            html.append("</tr>\n");
            header = false;
        }
        html.append("</table>\n");
        rows.clear();
    }

    /** Applies inline formatting to already-escaped text. */
    private static String inline(String escaped) {
        List<String> codes = new ArrayList<>();
        Matcher code = CODE.matcher(escaped);
        StringBuilder withPlaceholders = new StringBuilder();
        while (code.find()) {
            codes.add(code.group(1));
            code.appendReplacement(withPlaceholders, "\u0000" + (codes.size() - 1) + "\u0000");
        }
        code.appendTail(withPlaceholders);

        String result = LINK.matcher(withPlaceholders.toString())
                .replaceAll(m -> Matcher.quoteReplacement(
                        "<a href=\"" + m.group(2) + "\" rel=\"noopener\">" + m.group(1) + "</a>"));
        result = BOLD.matcher(result).replaceAll("<strong>$1</strong>");
        for (int i = 0; i < codes.size(); i++) {
            result = result.replace("\u0000" + i + "\u0000", "<code>" + codes.get(i) + "</code>");
        }
        return result;
    }

    static String escape(String text) {
        // Keep entities that ReportRenderer already wrote (&lt; &gt; ...), escape every other '&'
        return text.replaceAll("&(?!(lt|gt|amp|quot|#39);)", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
