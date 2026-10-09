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
    @DisplayName("a quote the live transcriber cut into several segments lands on the one where it starts")
    void acrossSegments() {
        List<TranscriptSegment> live = List.of(
                new TranscriptSegment(SESSION, 1, "0", "Somos un restaurante y queremos reservas en línea.", 0, 1000, true),
                new TranscriptSegment(SESSION, 2, "0", "Como cliente, quiero reservar una mesa desde la página web,", 1000, 2000, true),
                new TranscriptSegment(SESSION, 3, "0", "eligiendo la fecha, la hora y el número de personas,", 2000, 3000, true),
                new TranscriptSegment(SESSION, 4, "0", "para no tener que llamar por teléfono.", 3000, 4000, true));

        assertThat(QuoteLocator.locate("Como cliente, quiero reservar una mesa desde la página web, eligiendo la "
                + "fecha, la hora y el número de personas, para no tener que llamar por teléfono.", live)).isEqualTo(2);
        assertThat(QuoteLocator.locate("una mesa desde la pagina web eligiendo la fecha", live)).isEqualTo(2);
        assertThat(QuoteLocator.of(live.reversed()).sequenceOf(
                "reservar una mesa en la web eligiendo fecha, hora y número de personas")).isEqualTo(2);
    }

    @Test
    @DisplayName("answers null when nothing in the window says it")
    void none() {
        assertThat(QuoteLocator.locate("necesitamos exportar a Excel", SEGMENTS)).isNull();
        assertThat(QuoteLocator.locate(null, SEGMENTS)).isNull();
        assertThat(QuoteLocator.NONE.sequenceOf("cualquier cosa")).isNull();
    }
}
