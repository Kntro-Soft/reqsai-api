package com.kntro.reqsai.billing.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain: MeetingEstimate")
class MeetingEstimateTest {

    @Test
    @DisplayName("uses the measured rate once there are enough minutes of audio")
    void measured_rate() {
        // 30,000 live tokens over 10 minutes of audio = 3,000 per minute
        MeetingEstimate estimate = MeetingEstimate.of(60_000, 30_000, 600);

        assertThat(estimate.measured()).isTrue();
        assertThat(estimate.tokensPerMinute()).isEqualTo(3_000);
        assertThat(estimate.minutesLeft()).isEqualTo(20);
    }

    @Test
    @DisplayName("falls back to the documented constant with too little audio")
    void default_rate() {
        MeetingEstimate estimate = MeetingEstimate.of(60_000, 5_000, 30);

        assertThat(estimate.measured()).isFalse();
        assertThat(estimate.tokensPerMinute()).isEqualTo(MeetingEstimate.DEFAULT_TOKENS_PER_MINUTE);
        assertThat(estimate.minutesLeft()).isEqualTo(60_000 / MeetingEstimate.DEFAULT_TOKENS_PER_MINUTE);
    }

    @Test
    @DisplayName("an exhausted allowance leaves no minutes")
    void exhausted() {
        assertThat(MeetingEstimate.of(-500, 0, 0).minutesLeft()).isZero();
    }
}
