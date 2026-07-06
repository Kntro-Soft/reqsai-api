package com.kntro.reqsai.discovery.suggestionquality;

import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic, <em>test-programmable</em> stand-in for the LLM generation adapter. Each scenario sets the
 * exact {@link GenerationResult} the "model" will emit ({@link #setResult}) so the test controls the emitted
 * suggestion type/title/targetStoryId, while the real dedup/classification/persistence logic downstream runs
 * unchanged against a real Postgres+pgvector. It also captures the {@link GenerationContext} it was handed on
 * the last call ({@link #lastContext()}), so a test can assert the prompt carried the pending-suggestion ids.
 *
 * <p>Marked {@code @Primary} so it overrides the real Gemini adapter with no API key.
 */
@TestConfiguration
public class ProgrammableGenerationConfig {

    /** Shared, mutable holder the test writes and the port reads. Registered as a bean so the test injects it. */
    public static final class GenerationScript {
        private volatile GenerationResult next = new GenerationResult(List.of());
        private final AtomicReference<@Nullable GenerationContext> lastContext = new AtomicReference<>();

        public void setResult(GenerationResult result) {
            this.next = result;
        }

        public @Nullable GenerationContext lastContext() {
            return lastContext.get();
        }

        GenerationResult next() {
            return next;
        }

        void recordContext(@Nullable GenerationContext context) {
            lastContext.set(context);
        }
    }

    @Bean
    public GenerationScript generationScript() {
        return new GenerationScript();
    }

    @Bean
    @Primary
    public RequirementGenerationPort programmableGenerationPort(GenerationScript script) {
        return new RequirementGenerationPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public GenerationResult generate(String transcript, String language) {
                return script.next();
            }

            @Override
            public GenerationResult generate(String transcript, String language, @Nullable GenerationContext context) {
                script.recordContext(context);
                return script.next();
            }
        };
    }
}
