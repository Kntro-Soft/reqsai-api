package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.workspace.application.port.DocumentClassification;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationPort;
import com.kntro.reqsai.workspace.application.port.DocumentClassificationRequest;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic stand-in for the LLM document classifier (US22 client documents), so integration tests
 * exercise upload → classification → apply without an AI key. It always proposes the same summary,
 * glossary terms and constraints, and remembers the last request so tests can assert which text reached
 * the model.
 * <p>
 * Usage: {@code @Import(StubDocumentClassificationConfig.class)}.
 */
@TestConfiguration
public class StubDocumentClassificationConfig {

    public static final String SUMMARY =
            "La Tradición es una cadena de restaurantes de Lima que quiere reservas de mesa por Internet.";

    public static final DocumentClassification STUB_RESULT = new DocumentClassification(
            SUMMARY,
            DocumentType.TECHNICAL_SPEC,
            List.of(
                    new DocumentClassification.SuggestedTerm("Comensal", "Cliente que reserva una mesa en el restaurante."),
                    new DocumentClassification.SuggestedTerm("Reserva", "Mesa apartada para una fecha, hora y número de personas."),
                    new DocumentClassification.SuggestedTerm("comensal", "Duplicado que la normalización descarta.")),
            List.of(
                    "El sistema debe cumplir la Ley 29733 de protección de datos personales.",
                    "La aplicación debe estar disponible el 99,5 % del tiempo."));

    /** The last request the stub classified. */
    public static final AtomicReference<DocumentClassificationRequest> LAST_REQUEST = new AtomicReference<>();

    @Bean
    @Primary
    public DocumentClassificationPort stubDocumentClassificationPort() {
        return new DocumentClassificationPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public DocumentClassification classify(DocumentClassificationRequest request) {
                LAST_REQUEST.set(request);
                return STUB_RESULT;
            }
        };
    }
}
