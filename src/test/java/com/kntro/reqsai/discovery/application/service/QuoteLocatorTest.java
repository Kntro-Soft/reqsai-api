package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuoteLocator")
class QuoteLocatorTest {

    private static final UUID SESSION = UUID.randomUUID();

    private static final List<TranscriptSegment> SEGMENTS = List.of(
            new TranscriptSegment(SESSION, 4, "0", "Buenas tardes, gracias por venir.", 0, 1000, true),
            new TranscriptSegment(SESSION, 5, "1", "Queremos que el comensal pueda cancelar su reserva hasta veinticuatro horas antes.", 1000, 5000, true),
            new TranscriptSegment(SESSION, 6, "0", "Perfecto, ¿y el pago con Yape?", 5000, 7000, true));

    @Test
    @DisplayName("finds the segment holding the quote, ignoring case, accents and punctuation")
    void exact() {
        assertThat(QuoteLocator.locate("el comensal pueda CANCELAR su reserva", SEGMENTS)).isEqualTo(5);
        assertThat(QuoteLocator.locate("Perfecto, y el pago con yape", SEGMENTS)).isEqualTo(6);
    }

    @Test
    @DisplayName("tolerates a trimmed or slightly reworded quote")
    void approximate() {
        assertThat(QuoteLocator.locate("comensal cancelar reserva veinticuatro horas", SEGMENTS)).isEqualTo(5);
    }

    @Test
    @DisplayName("answers null when nothing in the window says it")
    void none() {
        assertThat(QuoteLocator.locate("necesitamos exportar a Excel", SEGMENTS)).isNull();
        assertThat(QuoteLocator.locate(null, SEGMENTS)).isNull();
        assertThat(QuoteLocator.NONE.sequenceOf("cualquier cosa")).isNull();
    }
}
