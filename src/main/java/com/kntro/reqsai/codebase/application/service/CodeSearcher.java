package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical search over the source files of a repository: the retrieval half of the deep code search. The query
 * (a question or a story) becomes weighted stems (identifiers and quoted terms weigh double); every file is cut
 * into overlapping windows of lines, each scored BM25-like (rare terms count more, repeated ones saturate) with
 * a bonus when the file path itself matches and a boost for files of the modules the index already points at.
 * The best windows come back tightened around their hits, merged when they overlap, with their line numbers.
 *
 * <p>Words and code identifiers meet on a six-letter stem, so a Spanish request ("cancelar una reserva") finds
 * English code ({@code cancelReservation}, {@code CANCELLATION_LIMIT_HOURS}) as long as they share a root.
 * Pure and stateless; the caller removes secrets from the files first.
 */
public final class CodeSearcher {

    static final int WINDOW_LINES = 24;
    static final int STRIDE = 12;
    static final int CONTEXT_LINES = 3;
    static final int MAX_MERGED_LINES = 48;
    static final int MAX_LINE_CHARS = 240;
    static final int MAX_TERMS = 24;
    static final int MAX_PER_FILE = 3;
    static final double PREFERRED_BOOST = 1.5;
    static final double PATH_BONUS = 0.6;
    static final double RELATIVE_FLOOR = 0.25;
    private static final double K1 = 1.2;
    private static final int STEM_LENGTH = 6;
    private static final int MIN_REMAINING_CHARS = 300;

    private static final Pattern IDENTIFIER = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9]*(?:[_.\\-][A-Za-z0-9]+)+|[a-z][a-z0-9]*[A-Z][A-Za-z0-9]*|[A-Z][a-z0-9]+[A-Z][A-Za-z0-9]*");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]{2,80})\"|`([^`]{2,80})`|'([^']{3,80})'|«([^»]{2,80})»");

    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
            "para", "como", "quiero", "queremos", "puede", "pueda", "puedan", "poder", "desde", "hasta", "cuando",
            "donde", "cual", "cuales", "sobre", "entre", "esta", "este", "estos", "estas", "esto", "tiene", "tener",
            "tengo", "hacer", "cada", "todo", "todos", "toda", "todas", "debe", "deben", "deberia", "seria", "sido",
            "pero", "porque", "tambien", "ademas", "otro", "otra", "otros", "otras", "algo", "alguien", "hay",
            "existe", "existen", "tenga", "tengan", "sean", "ser", "estar", "esta", "estan", "algun", "alguna",
            "nuestro", "nuestra", "mismo", "misma", "solo", "sino", "segun", "mas", "menos", "muy", "con", "sin",
            "that", "this", "with", "from", "have", "has", "will", "would", "should", "could", "can", "when",
            "where", "which", "what", "want", "wants", "need", "needs", "into", "than", "then", "there", "their",
            "they", "them", "been", "being", "also", "does", "each", "every", "only", "such", "some", "more",
            "los", "las", "que", "una", "uno", "por", "del", "sus", "fue", "son", "sea", "the", "and", "for", "are", "not", "but", "you", "all", "any", "can", "its", "was", "our",
            "less", "must", "able", "like", "about", "after", "before", "user", "story", "feature"));

    private CodeSearcher() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** A passage of a file with its line range ({@code lineStart}..{@code lineEnd}, 1-based, inclusive). */
    public record Passage(String path, int lineStart, int lineEnd, String text, double score) {
    }

    /** The query as weighted stems. */
    public record Query(Map<String, Double> stems) {

        public boolean isEmpty() {
            return stems.isEmpty();
        }
    }

    /** The stems of {@code text}: words of 4+ letters (not stop words), numbers, identifiers and quoted terms. */
    public static Query parse(@Nullable String text) {
        Map<String, Double> stems = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return new Query(stems);
        String plain = fold(text);
        Matcher quoted = QUOTED.matcher(plain);
        while (quoted.find()) {
            for (int g = 1; g <= 4; g++) {
                if (quoted.group(g) != null) addAll(stems, quoted.group(g), 2.0, 3);
            }
        }
        Matcher identifier = IDENTIFIER.matcher(plain);
        while (identifier.find()) addAll(stems, identifier.group(), 2.0, 3);
        addAll(stems, plain, 1.0, 3);
        if (stems.size() <= MAX_TERMS) return new Query(stems);
        Map<String, Double> capped = new LinkedHashMap<>();
        stems.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(MAX_TERMS)
                .forEach(e -> capped.put(e.getKey(), e.getValue()));
        return new Query(capped);
    }

    private static void addAll(Map<String, Double> stems, String text, double weight, int minLength) {
        for (String token : tokens(text)) {
            boolean number = Character.isDigit(token.charAt(0));
            if (number ? token.length() > 4 : token.length() < minLength) continue;
            if (STOPWORDS.contains(token)) continue;
            stems.merge(stem(token), number ? Math.min(weight, 0.7) : weight, Math::max);
        }
    }

    /**
     * The best passages of {@code files} for {@code query}, best first, at most {@code maxPassages} and
     * {@code maxChars} characters of text in total. Files under a {@code preferredPrefixes} folder score 1.5x.
     * Stops early (returning what it has) once {@code expired} is true.
     */
    public static List<Passage> search(List<SourceFile> files, Query query, Collection<String> preferredPrefixes,
                                       int maxPassages, int maxChars, BooleanSupplier expired) {
        if (query.isEmpty() || files.isEmpty() || maxPassages <= 0 || maxChars <= 0) return List.of();
        List<String> terms = List.copyOf(query.stems().keySet());
        double[] weights = terms.stream().mapToDouble(query.stems()::get).toArray();
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < terms.size(); i++) index.put(terms.get(i), i);

        List<Scanned> scanned = new ArrayList<>(files.size());
        int[] documentFrequency = new int[terms.size()];
        for (SourceFile file : files) {
            if (expired.getAsBoolean()) break;
            Scanned s = scan(file, index, terms.size());
            if (s == null) continue;
            scanned.add(s);
            for (int t = 0; t < terms.size(); t++) {
                if (s.fileTf[t] > 0 || s.pathHit[t]) documentFrequency[t]++;
            }
        }
        if (scanned.isEmpty()) return List.of();

        double[] idf = new double[terms.size()];
        for (int t = 0; t < terms.size(); t++) {
            idf[t] = Math.log(1 + (files.size() - documentFrequency[t] + 0.5) / (documentFrequency[t] + 0.5));
        }

        List<Window> windows = new ArrayList<>();
        for (Scanned s : scanned) {
            if (expired.getAsBoolean()) break;
            double boost = preferred(s.file.path(), preferredPrefixes) ? PREFERRED_BOOST : 1.0;
            windows.addAll(bestWindows(s, idf, weights, boost));
        }
        windows.sort(Comparator.comparingDouble(Window::score).reversed());
        if (windows.isEmpty()) return List.of();

        double floor = windows.getFirst().score() * RELATIVE_FLOOR;
        List<Window> chosen = new ArrayList<>();
        for (Window w : windows) {
            if (w.score() < floor || chosen.size() >= maxPassages) continue;
            merge(chosen, w);
        }
        chosen.sort(Comparator.comparingDouble(Window::score).reversed());
        return render(chosen, maxPassages, maxChars);
    }

    // ---- scanning ----

    private record Scanned(SourceFile file, String[] lines, int[][] lineTf, int[] fileTf, boolean[] pathHit) {
    }

    private record Window(Scanned source, int start, int end, double score) {
    }

    private static @Nullable Scanned scan(SourceFile file, Map<String, Integer> index, int termCount) {
        String[] lines = file.content().split("\n", -1);
        int[][] lineTf = new int[lines.length][];
        int[] fileTf = new int[termCount];
        boolean any = false;
        for (int i = 0; i < lines.length; i++) {
            for (String token : tokens(fold(lines[i]))) {
                Integer t = index.get(stem(token));
                if (t == null) continue;
                if (lineTf[i] == null) lineTf[i] = new int[termCount];
                lineTf[i][t]++;
                fileTf[t]++;
                any = true;
            }
        }
        boolean[] pathHit = new boolean[termCount];
        for (String token : tokens(fold(file.path()))) {
            Integer t = index.get(stem(token));
            if (t != null) {
                pathHit[t] = true;
                any = true;
            }
        }
        return any ? new Scanned(file, lines, lineTf, fileTf, pathHit) : null;
    }

    private static List<Window> bestWindows(Scanned s, double[] idf, double[] weights, double boost) {
        int n = s.lines.length;
        double pathBonus = 0;
        for (int t = 0; t < weights.length; t++) {
            if (s.pathHit[t]) pathBonus += PATH_BONUS * idf[t] * weights[t];
        }
        List<Window> all = new ArrayList<>();
        int[] tf = new int[weights.length];
        for (int start = 0; start < n; start += STRIDE) {
            int end = Math.min(n, start + WINDOW_LINES);
            java.util.Arrays.fill(tf, 0);
            int first = -1;
            int last = -1;
            for (int i = start; i < end; i++) {
                int[] counts = s.lineTf[i];
                if (counts == null) continue;
                for (int t = 0; t < counts.length; t++) tf[t] += counts[t];
                if (first < 0) first = i;
                last = i;
            }
            if (first >= 0) {
                double score = pathBonus;
                for (int t = 0; t < tf.length; t++) {
                    if (tf[t] > 0) score += idf[t] * weights[t] * (tf[t] * (K1 + 1)) / (tf[t] + K1);
                }
                int from = Math.max(start, first - CONTEXT_LINES);
                int to = Math.min(end, last + CONTEXT_LINES + 1);
                all.add(new Window(s, from, to, score * boost));
            } else if (end == n) {
                break;
            }
            if (end == n) break;
        }
        all.sort(Comparator.comparingDouble(Window::score).reversed());
        return all.size() <= MAX_PER_FILE ? all : new ArrayList<>(all.subList(0, MAX_PER_FILE));
    }

    private static void merge(List<Window> chosen, Window w) {
        for (int i = 0; i < chosen.size(); i++) {
            Window c = chosen.get(i);
            if (c.source != w.source || w.start > c.end || w.end < c.start) continue;
            int start = Math.min(c.start, w.start);
            int end = Math.max(c.end, w.end);
            if (end - start <= MAX_MERGED_LINES) {
                chosen.set(i, new Window(c.source, start, end, Math.max(c.score, w.score)));
            }
            return;
        }
        chosen.add(w);
    }

    private static List<Passage> render(List<Window> chosen, int maxPassages, int maxChars) {
        List<Passage> out = new ArrayList<>();
        int used = 0;
        for (Window w : chosen) {
            if (out.size() >= maxPassages) break;
            int remaining = maxChars - used;
            if (remaining < MIN_REMAINING_CHARS) break;
            int start = w.start;
            int end = w.end;
            while (start < end - 1 && w.source.lines[start].isBlank()) start++;
            while (end - 1 > start && w.source.lines[end - 1].isBlank()) end--;
            StringBuilder text = new StringBuilder();
            int lastLine = start;
            for (int i = start; i < end; i++) {
                String line = w.source.lines[i].stripTrailing();
                if (line.length() > MAX_LINE_CHARS) line = line.substring(0, MAX_LINE_CHARS - 3) + "...";
                if (text.length() + line.length() + 1 > remaining && i > start) break;
                if (i > start) text.append('\n');
                text.append(line);
                lastLine = i;
            }
            used += text.length();
            out.add(new Passage(w.source.file.path(), start + 1, lastLine + 1, text.toString(), w.score));
        }
        return out;
    }

    private static boolean preferred(String path, Collection<String> prefixes) {
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isEmpty()) continue;
            if (path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/")) return true;
        }
        return false;
    }

    // ---- tokens ----

    static String stem(String token) {
        return token.length() > STEM_LENGTH ? token.substring(0, STEM_LENGTH) : token;
    }

    /** Lower-case, accent-free text. */
    static String fold(String text) {
        boolean ascii = true;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                ascii = false;
                break;
            }
        }
        return ascii ? text : Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Words, numbers and the parts of identifiers (camelCase, snake_case, kebab-case) of {@code text}, lower-case. */
    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                flush(sb, out);
                continue;
            }
            if (sb.length() > 0 && Character.isUpperCase(c)) {
                char previous = text.charAt(i - 1);
                boolean lowerThenUpper = Character.isLowerCase(previous) || Character.isDigit(previous);
                boolean acronymEnd = Character.isUpperCase(previous) && i + 1 < n
                        && Character.isLowerCase(text.charAt(i + 1));
                if (lowerThenUpper || acronymEnd) flush(sb, out);
            }
            sb.append(c);
        }
        flush(sb, out);
        return out;
    }

    private static void flush(StringBuilder sb, List<String> out) {
        if (sb.isEmpty()) return;
        if (sb.length() >= 2) out.add(sb.toString().toLowerCase(Locale.ROOT));
        sb.setLength(0);
    }
}
