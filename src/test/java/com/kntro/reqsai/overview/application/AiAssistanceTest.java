package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.overview.application.result.AiAssistance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application: copilot acceptance rate")
class AiAssistanceTest {

    @Test
    @DisplayName("is the accepted share of the decided proposals; pending ones and questions do not count")
    void rate() {
        assertThat(new AiAssistance(3, 1, 10, 4).acceptanceRate()).isEqualTo(0.75);
        assertThat(new AiAssistance(0, 5, 0, 0).acceptanceRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("is unknown until a proposal has been decided")
    void unknownWithoutDecisions() {
        assertThat(new AiAssistance(0, 0, 7, 2).acceptanceRate()).isNull();
    }
}
