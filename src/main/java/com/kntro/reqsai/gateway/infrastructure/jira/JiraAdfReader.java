package com.kntro.reqsai.gateway.infrastructure.jira;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Minimal inverse of {@link JiraAdfBuilder}: flattens an Atlassian Document Format (ADF) description node
 * (Jira Cloud REST v3 returns descriptions as ADF, not plain text) into plain text so the import mapping
 * can feed it to the LLM / fallback parser.
 *
 * <p>Walks the {@code content} tree collecting every {@code text} leaf, inserting a newline after each
 * block node ({@code paragraph}, {@code heading}, {@code listItem}) and a {@code "- "} bullet marker before
 * list items. Unknown node types are traversed for their text children. Returns {@code ""} for a null or
 * empty document — never throws, so a malformed description never aborts an import.
 */
public final class JiraAdfReader {

    private JiraAdfReader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Flattens the ADF {@code doc} map to plain text (empty string when null/blank). */
    public static String toPlainText(@Nullable Map<String, Object> adf) {
        if (adf == null || adf.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        appendNode(adf, out);
        return out.toString().strip();
    }

    @SuppressWarnings("unchecked")
    private static void appendNode(Object node, StringBuilder out) {
        if (!(node instanceof Map<?, ?> map)) {
            return;
        }
        String type = String.valueOf(map.get("type"));
        if ("text".equals(type)) {
            Object text = map.get("text");
            if (text != null) {
                out.append(text);
            }
            return;
        }
        if ("hardBreak".equals(type)) {
            out.append('\n');
            return;
        }
        if ("listItem".equals(type)) {
            out.append("- ");
        }
        Object content = map.get("content");
        if (content instanceof List<?> children) {
            for (Object child : children) {
                appendNode(child, out);
            }
        }
        if (isBlock(type)) {
            out.append('\n');
        }
    }

    private static boolean isBlock(String type) {
        return switch (type) {
            case "paragraph", "heading", "listItem", "blockquote", "codeBlock" -> true;
            default -> false;
        };
    }
}
