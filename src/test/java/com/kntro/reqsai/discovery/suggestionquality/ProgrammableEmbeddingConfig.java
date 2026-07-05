package com.kntro.reqsai.discovery.suggestionquality;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, <em>concept-controlled</em> embedding stand-in for the suggestion-quality red-defect
 * integration test. Unlike the shared {@code StubEmbeddingConfig} (which maps every distinct string to a
 * near-orthogonal vector — so paraphrases score cosine ~0), this fake lets the test decide which texts
 * are near-duplicates by tagging them with an explicit concept marker.
 *
 * <h2>How a concept controls the vector</h2>
 * Any text containing a marker {@code [[concept]]} is embedded as the deterministic unit basis vector of
 * that concept plus a tiny, deterministic per-text perturbation. Two texts sharing a concept therefore land
 * almost on top of each other (cosine well above the 0.84 dedup threshold — a controlled "near-duplicate
 * paraphrase"), while two texts with different concepts get near-orthogonal basis vectors (cosine ~0 — a
 * "clearly distinct" requirement). The perturbation keeps twins from being bit-identical (so we exercise the
 * &lt;=&gt; distance path, not an equality shortcut) while staying comfortably inside the threshold.
 *
 * <p>Text without a marker falls back to a stable hash-based Gaussian vector (distinct from every concept),
 * so incidental embeddings the pipeline computes (e.g. the recent-transcript query embedding) never collide
 * with a seeded concept by accident.
 *
 * <p>Marked {@code @Primary} so it overrides the real Spring AI adapter without Ollama/Gemini/OpenAI.
 */
@TestConfiguration
public class ProgrammableEmbeddingConfig {

    /** Marker syntax: {@code [[some-concept]]} anywhere in the embedded text. */
    private static final Pattern CONCEPT = Pattern.compile("\\[\\[([^\\]]+)]]");

    /** Wraps {@code concept} in the marker the embedder recognises. Prepend to any seeded/drafted text. */
    public static String tag(String concept) {
        return "[[" + concept + "]] ";
    }

    @Bean
    @Primary
    public EmbeddingPort programmableEmbeddingPort() {
        return new EmbeddingPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public float[] embed(String text) {
                Matcher m = CONCEPT.matcher(text == null ? "" : text);
                if (m.find()) {
                    return conceptVector(m.group(1), text);
                }
                return hashVector(text);
            }
        };
    }

    /**
     * Unit basis vector for {@code concept} (deterministic, orthogonal across concepts because each lives
     * on its own coordinate) plus a tiny deterministic perturbation seeded by the full text, so twins are
     * near-identical (cosine ~0.999) but not bit-identical.
     */
    private static float[] conceptVector(String concept, String fullText) {
        int dims = EmbeddingPort.DIMENSIONS;
        // Spread concepts across the first (dims-32) coordinates; the tail holds the perturbation so it
        // never lands on a concept axis and cannot flip the sign of the dominant component.
        int conceptAxis = Math.floorMod(concept.hashCode(), dims - 32);

        float[] v = new float[dims];
        v[conceptAxis] = 1.0f; // dominant component: fixes the direction for this concept

        Random noise = new Random(fullText.hashCode());
        for (int i = dims - 32; i < dims; i++) {
            // Perturbation magnitude ~0.03 keeps cosine(twin, base) ≈ 1 / sqrt(1 + 32*0.03^2) ≈ 0.9986,
            // and cosine between two twins of the same concept ≈ 0.997 — far above the 0.84 threshold.
            v[i] = (float) (noise.nextGaussian() * 0.03);
        }
        return v;
    }

    /** Stable Gaussian vector for un-tagged text; near-orthogonal to every concept basis vector. */
    private static float[] hashVector(String text) {
        Random rnd = new Random(text == null ? 0 : text.hashCode());
        float[] v = new float[EmbeddingPort.DIMENSIONS];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) rnd.nextGaussian();
        }
        return v;
    }
}
