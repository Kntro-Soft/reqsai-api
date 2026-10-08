package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShareLink")
class ShareLinkTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    @DisplayName("stores only the token hash and expires after the chosen days")
    void issue() {
        ShareLink link = ShareLink.issue(UUID.randomUUID(), UUID.randomUUID(), "raw-token", 14, NOW);

        assertThat(link.getTokenHash()).isEqualTo(HashUtils.sha256("raw-token")).isNotEqualTo("raw-token");
        assertThat(link.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(link.isActive(NOW)).isTrue();
        assertThat(link.isActive(NOW.plus(Duration.ofDays(14)))).isFalse();
    }

    @Test
    @DisplayName("refuses a validity outside 1 to 90 days")
    void validity_bounds() {
        assertThatThrownBy(() -> ShareLink.issue(UUID.randomUUID(), UUID.randomUUID(), "t", 0, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ShareLink.issue(UUID.randomUUID(), UUID.randomUUID(), "t", 91, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a revoked link stops working and keeps its first revocation time")
    void revoke() {
        ShareLink link = ShareLink.issue(UUID.randomUUID(), UUID.randomUUID(), "t", 7, NOW);

        link.revoke(NOW.plusSeconds(60));
        link.revoke(NOW.plusSeconds(120));

        assertThat(link.isActive(NOW.plusSeconds(90))).isFalse();
        assertThat(link.getRevokedAt()).isEqualTo(NOW.plusSeconds(60));
    }
}
