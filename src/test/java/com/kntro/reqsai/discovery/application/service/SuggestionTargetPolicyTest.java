package com.kntro.reqsai.discovery.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SuggestionTargetPolicy} on the similarities measured in production ({@code text-embedding-3-small},
 * draft candidate text vs. backlog story): every refinement of the right story stays attached, and every
 * wrong target seen in production is detached.
 */
@DisplayName("Application: suggestion target policy (production calibration)")
class SuggestionTargetPolicyTest {

    private final SuggestionTargetPolicy policy = new SuggestionTargetPolicy(0.60, 0.05);

    @ParameterizedTest(name = "{0}: sim {1}, closest {2} → keep")
    @CsvSource({
            "copago on 'Cálculo de tarifas',                    0.816, 0.816",
            "reprogramar al bloquear on 'Bloqueo de horarios',  0.764, 0.764",
            "sustitución on 'Gestión de productos agotados',    0.741, 0.741",
            "penalidad 10 % on 'Cancelar una cita',             0.701, 0.701",
            "anticipación 30 días on 'Reserva de citas',        0.685, 0.685",
            "devolución on 'Cancelar una cita',                 0.653, 0.675",
            "horario tomado on 'Reserva de citas',              0.624, 0.671",
    })
    void keeps_refinements_of_the_right_story(String label, double similarity, double closest) {
        assertThat(policy.accepts(similarity, closest)).as(label).isTrue();
    }

    @ParameterizedTest(name = "{0}: sim {1}, closest {2} → detach")
    @CsvSource({
            "lista de espera on 'Registro de menores',         0.491, 0.700",
            "reprogramar on 'Cálculo de tarifas',              0.484, 0.947",
            "reprogramar on 'Bloqueo de horarios',             0.594, 0.947",
            "delivery on 'Productos de la bodega cercana',     0.396, 0.424",
            "repartidor on 'Gestión de productos agotados',    0.500, 0.508",
            "inasistencias on 'Reprogramar una cita',          0.502, 0.634",
    })
    void detaches_the_wrong_targets_seen_in_production(String label, double similarity, double closest) {
        assertThat(policy.accepts(similarity, closest)).as(label).isFalse();
    }
}
