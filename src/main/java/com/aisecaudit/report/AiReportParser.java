package com.aisecaudit.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the model's answer into an {@link AiReport}.
 * Models sometimes wrap JSON in ```json fences or add a sentence before it,
 * so the parser looks for the outermost JSON object and reads fields leniently.
 */
@Component
public class AiReportParser {

    private static final Logger log = LoggerFactory.getLogger(AiReportParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<AiReport> parse(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            return Optional.empty();
        }
        int start = modelText.indexOf('{');
        int end = modelText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("AI answer contains no JSON object, falling back to raw text");
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(modelText.substring(start, end + 1));
            List<AiFinding> findings = new ArrayList<>();
            for (JsonNode node : root.path("findings")) {
                String title = text(node, "title");
                if (title == null) {
                    continue;
                }
                findings.add(new AiFinding(
                        ReportSeverity.parse(text(node, "severity")),
                        title,
                        text(node, "file"),
                        node.path("line").canConvertToInt() && node.path("line").asInt() > 0
                                ? node.path("line").asInt() : null,
                        text(node, "risk"),
                        text(node, "fix")));
            }
            return Optional.of(new AiReport(
                    text(root, "summary"),
                    findings,
                    strings(root.path("falsePositives")),
                    strings(root.path("priorities"))));
        } catch (Exception e) {
            log.warn("AI answer is not valid JSON ({}), falling back to raw text", e.getMessage());
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            String text = item.asText().trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }
}
