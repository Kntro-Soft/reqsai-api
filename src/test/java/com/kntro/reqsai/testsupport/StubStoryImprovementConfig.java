package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementPort;
import com.kntro.reqsai.discovery.application.port.StoryImprovementRequest;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic stand-in for the AI that improves a story (US73), so integration tests exercise propose and
 * apply without an AI key. It proposes {@link #WITH_PENDING_VALUE} unless a test sets another result, counts its
 * calls and remembers the last request so tests can assert what reached the model.
 * <p>
 * Usage: {@code @Import(StubStoryImprovementConfig.class)}; call {@link #reset()} before each test.
 */
@TestConfiguration
public class StubStoryImprovementConfig {

    public static final String TOKEN = "[[tiempo máximo de respuesta]]";

    /** An improved story with one pending value, as the model would propose it for a vague story. */
    public static final StoryImprovementResult WITH_PENDING_VALUE = new StoryImprovementResult(
            new StoryDraft("Exportar el backlog a CSV", "analista de cumplimiento",
                    "exportar el backlog aprobado a un archivo CSV", "compartirlo con el cliente sin reescribirlo",
                    List.of(new StoryDraft.CriterionDraft("Exportación correcta", "hay historias aprobadas en el backlog",
                            "el analista exporta el backlog",
                            "el sistema descarga un archivo CSV en menos de " + TOKEN + " segundos"))),
            List.of(new StoryImprovementResult.PendingValue(TOKEN, "¿En cuántos segundos debe responder?")),
            List.of("Se reemplazó «rápido» por un valor pendiente"));

    public static final AtomicReference<StoryImprovementResult> NEXT = new AtomicReference<>(WITH_PENDING_VALUE);
    public static final AtomicReference<StoryImprovementRequest> LAST_REQUEST = new AtomicReference<>();
    public static final AtomicInteger CALLS = new AtomicInteger();

    public static void reset() {
        NEXT.set(WITH_PENDING_VALUE);
        LAST_REQUEST.set(null);
        CALLS.set(0);
    }

    @Bean
    @Primary
    public StoryImprovementPort stubStoryImprovementPort() {
        return new StoryImprovementPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public StoryImprovementResult improve(StoryImprovementRequest request) {
                CALLS.incrementAndGet();
                LAST_REQUEST.set(request);
                return NEXT.get();
            }
        };
    }
}
