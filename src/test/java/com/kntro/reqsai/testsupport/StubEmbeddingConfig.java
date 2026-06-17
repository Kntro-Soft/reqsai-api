package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.discovery.application.port.EmbeddingPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Random;

/**
 * Deterministic stand-in for the Spring AI embedding model, shared by all integration tests that
 * exercise the deduplication path.
 * <p>
 * Identical text → identical vector (cosine 1.0 → duplicate). Different text → near-orthogonal
 * vector (cosine ≈ 0 → distinct). Marked {@code @Primary} so it overrides
 * {@code SpringAiEmbeddingAdapter} in tests without needing Ollama or Gemini configured.
 * <p>
 * Usage: {@code @Import(StubEmbeddingConfig.class)} on any {@code @SpringBootTest} that needs
 * the embedding port to be available.
 */
@TestConfiguration
public class StubEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingPort stubEmbeddingPort() {
        return new EmbeddingPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public float[] embed(String text) {
                Random rnd = new Random(text.hashCode());
                float[] vector = new float[EmbeddingPort.DIMENSIONS];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) rnd.nextGaussian();
                }
                return vector;
            }
        };
    }
}
