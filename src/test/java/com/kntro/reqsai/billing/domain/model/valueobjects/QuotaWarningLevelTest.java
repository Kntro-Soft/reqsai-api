package com.kntro.reqsai.billing.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain: QuotaWarningLevel")
class QuotaWarningLevelTest {

    @Test
    @DisplayName("steps at 80, 90 and 100 percent of the allowance")
    void steps() {
        assertThat(QuotaWarningLevel.of(0, 100_000)).isEqualTo(QuotaWarningLevel.NONE);
        assertThat(QuotaWarningLevel.of(79_999, 100_000)).isEqualTo(QuotaWarningLevel.NONE);
        assertThat(QuotaWarningLevel.of(80_000, 100_000)).isEqualTo(QuotaWarningLevel.WARNING_80);
        assertThat(QuotaWarningLevel.of(89_999, 100_000)).isEqualTo(QuotaWarningLevel.WARNING_80);
        assertThat(QuotaWarningLevel.of(90_000, 100_000)).isEqualTo(QuotaWarningLevel.WARNING_90);
        assertThat(QuotaWarningLevel.of(99_999, 100_000)).isEqualTo(QuotaWarningLevel.WARNING_90);
        assertThat(QuotaWarningLevel.of(100_000, 100_000)).isEqualTo(QuotaWarningLevel.EXHAUSTED);
        assertThat(QuotaWarningLevel.of(250_000, 100_000)).isEqualTo(QuotaWarningLevel.EXHAUSTED);
    }

    @Test
    @DisplayName("a missing allowance never warns")
    void no_limit() {
        assertThat(QuotaWarningLevel.of(10, 0)).isEqualTo(QuotaWarningLevel.NONE);
    }

    @Test
    @DisplayName("round-trips through the stored percentage")
    void from_percent() {
        for (QuotaWarningLevel level : QuotaWarningLevel.values()) {
            assertThat(QuotaWarningLevel.fromPercent(level.percent())).isEqualTo(level);
        }
        assertThat(QuotaWarningLevel.fromPercent(95)).isEqualTo(QuotaWarningLevel.WARNING_90);
    }
}
