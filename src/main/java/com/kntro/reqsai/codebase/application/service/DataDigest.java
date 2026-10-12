package com.kntro.reqsai.codebase.application.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a JSON data file (translations, catalogs, configuration) into the business values it holds, one per
 * line as {@code path = value}: every number first, then every text that carries a figure. Array items are
 * named by their {@code name}/{@code id}/{@code title} when they have one, so
 * {@code pricing.plans[Pro].monthly = 49} says which plan costs what. Flags and numbered headings ("3. Cuentas")
 * say nothing about the business and are left out. A 30 KB translation file becomes a few dozen lines, its
 * figures at the top where an excerpt budget never cuts them.
 */
public final class DataDigest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FIGURE = Pattern.compile("\\d");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\d{1,3}[.)]\\s+\\D*$");
    private static final int MAX_LINES = 400;
    private static final int MAX_TEXT = 160;
    private static final List<String> NAME_KEYS = List.of("name", "nombre", "title", "titulo", "título", "id", "key");

    private DataDigest() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** The values of a JSON document, or null when the content is not JSON (the caller falls back to lines). */
    public static String flatten(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (root == null || !(root.isObject() || root.isArray())) return null;
        List<String> numbers = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        walk(root, "", numbers, texts);
        List<String> lines = new ArrayList<>(numbers);
        lines.addAll(texts);
        return lines.isEmpty() ? null : String.join("\n", lines.subList(0, Math.min(MAX_LINES, lines.size())));
    }

    private static void walk(JsonNode node, String path, List<String> numbers, List<String> texts) {
        if (numbers.size() + texts.size() >= MAX_LINES * 2) return;
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String child = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                walk(field.getValue(), child, numbers, texts);
            }
        } else if (node.isArray()) {
            int i = 0;
            for (JsonNode item : node) {
                walk(item, path + "[" + label(item, i) + "]", numbers, texts);
                i++;
            }
        } else if (node.isNumber()) {
            numbers.add(path + " = " + node.asString());
        } else if (node.isString()) {
            String text = node.asString().strip();
            if (FIGURE.matcher(text).find() && !NUMBERED_HEADING.matcher(text).matches()) {
                texts.add(path + " = \"" + (text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "…" : text) + "\"");
            }
        }
    }

    private static String label(JsonNode item, int index) {
        if (item.isObject()) {
            for (String key : NAME_KEYS) {
                JsonNode value = item.get(key);
                if (value != null && value.isValueNode() && !value.asString().isBlank()) {
                    String text = value.asString().strip();
                    return text.length() > 40 ? text.substring(0, 40) : text;
                }
            }
        }
        return Integer.toString(index);
    }
}
