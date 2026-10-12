package com.kntro.reqsai.discovery.infrastructure.ai.verification;

import com.kntro.reqsai.codebase.api.CodeSnippet;
import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the model's verdict. The model cites passages by key ({@code S1}) and lines; every citation is checked
 * against the passages it was shown: an unknown key is dropped, lines are clamped into the passage, the quote
 * must really appear there, and the link to GitHub is built here from the passage's repository and commit, never
 * taken from the model. A claim that the code has or contradicts something with no valid citation is not
 * believed: it becomes {@code UNSURE}.
 */
final class CodeVerificationParser {

    static final int MAX_REFERENCES = 5;
    static final int NOTE_MAX = 1000;
    static final int QUOTE_MAX = 200;

    private CodeVerificationParser() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** The key the model uses for the {@code index}-th (0-based) passage. */
    static String keyOf(int index) {
        return "S" + (index + 1);
    }

    /** @throws IllegalStateException when the reply is not a JSON object with a known verdict */
    static CodeVerification parse(ObjectMapper mapper, String reply, List<CodeSnippet> snippets) {
        String json = reply == null ? "" : reply.strip();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("no JSON object in the reply");
        }
        JsonNode root = mapper.readTree(json.substring(start, end + 1));
        CodeVerdict verdict = CodeVerdict.parse(text(root.path("verdict")));
        if (verdict == null) {
            throw new IllegalStateException("the reply has no known verdict");
        }
        String note = text(root.path("note"));
        List<CodeReference> references = verdict == CodeVerdict.NOT_FOUND ? List.of()
                : references(root.path("references"), snippets);
        if ((verdict == CodeVerdict.ALREADY_EXISTS || verdict == CodeVerdict.CONFLICTS_WITH_CODE)
                && references.isEmpty()) {
            verdict = CodeVerdict.UNSURE;
        }
        if (note == null) {
            note = "";
        } else if (note.length() > NOTE_MAX) {
            note = note.substring(0, NOTE_MAX - 3) + "...";
        }
        return new CodeVerification(verdict, note, references);
    }

    private static List<CodeReference> references(JsonNode array, List<CodeSnippet> snippets) {
        List<CodeReference> out = new ArrayList<>();
        if (array == null || !array.isArray()) return out;
        for (JsonNode node : array) {
            CodeSnippet snippet = snippetOf(node, snippets);
            if (snippet == null) continue;
            int from = clamp(node.path("lineStart").asInt(snippet.lineStart()), snippet);
            int to = clamp(node.path("lineEnd").asInt(from), snippet);
            if (to < from) {
                int swap = from;
                from = to;
                to = swap;
            }
            CodeReference reference = reference(snippet, from, to, quoteOf(text(node.path("quote")), snippet));
            if (out.stream().noneMatch(r -> sameSpot(r, reference))) out.add(reference);
            if (out.size() >= MAX_REFERENCES) break;
        }
        return out;
    }

    static CodeReference reference(CodeSnippet snippet, int lineStart, int lineEnd, @Nullable String quote) {
        String path = snippet.path();
        int slash = path.lastIndexOf('/');
        String folder = slash < 0 ? "" : path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        return new CodeReference(snippet.repository(), folder, name, urlFor(snippet, lineStart, lineEnd), path,
                lineStart, lineEnd, quote);
    }

    /** The passage's own link (its repository and commit) re-anchored on the cited lines. */
    private static String urlFor(CodeSnippet snippet, int lineStart, int lineEnd) {
        String url = snippet.url();
        int anchor = url.indexOf('#');
        String base = anchor < 0 ? url : url.substring(0, anchor);
        return base + "#L" + lineStart + (lineEnd > lineStart ? "-L" + lineEnd : "");
    }

    private static @Nullable CodeSnippet snippetOf(JsonNode node, List<CodeSnippet> snippets) {
        String key = text(node.path("snippet"));
        if (key != null) {
            String normalized = key.strip().toUpperCase(Locale.ROOT);
            for (int i = 0; i < snippets.size(); i++) {
                if (keyOf(i).equals(normalized)) return snippets.get(i);
            }
        }
        String file = text(node.path("file"));
        if (file != null) {
            for (CodeSnippet s : snippets) {
                if (s.path().equals(file.strip())) return s;
            }
        }
        return null;
    }

    private static int clamp(int line, CodeSnippet snippet) {
        return Math.max(snippet.lineStart(), Math.min(snippet.lineEnd(), line));
    }

    /** The quote when the passage really contains it (ignoring whitespace), shortened; null otherwise. */
    private static @Nullable String quoteOf(@Nullable String quote, CodeSnippet snippet) {
        if (quote == null) return null;
        String compact = quote.replaceAll("\\s+", " ").strip();
        if (compact.isEmpty() || !snippet.text().replaceAll("\\s+", " ").contains(compact)) return null;
        return compact.length() <= QUOTE_MAX ? compact : compact.substring(0, QUOTE_MAX - 3) + "...";
    }

    private static boolean sameSpot(CodeReference a, CodeReference b) {
        return a.repository().equals(b.repository()) && a.file().equals(b.file())
                && a.lineStart().equals(b.lineStart()) && a.lineEnd().equals(b.lineEnd());
    }

    private static @Nullable String text(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) return null;
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.strip();
    }
}
