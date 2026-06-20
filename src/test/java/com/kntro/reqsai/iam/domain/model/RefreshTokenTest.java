package com.kntro.reqsai.iam.domain.model;

import com.kntro.reqsai.iam.domain.model.TokenStatus;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link RefreshToken} aggregate.
 *
 * @see RefreshToken
 */
@DisplayName("Domain: RefreshToken Aggregate")
class RefreshTokenTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String RAW_TOKEN = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    private static final Instant FUTURE = Instant.now().plusSeconds(3600);
    private static final Instant PAST = Instant.now().minusSeconds(3600);

    @Test
    @DisplayName("issue sets status to ACTIVE")
    void issue_setsStatusActive() {
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, FUTURE);

        assertThat(token.getStatus()).isEqualTo(TokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("issue stores the SHA-256 hash, not the raw token")
    void issue_storesHashNotRaw() {
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, FUTURE);

        assertThat(token.getTokenHash())
                .isNotEqualTo(RAW_TOKEN)
                .isEqualTo(HashUtils.sha256(RAW_TOKEN));
    }

    @Test
    @DisplayName("isValid returns true when ACTIVE and not yet expired")
    void isValid_returnsTrueWhenActiveAndNotExpired() {
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, FUTURE);

        assertThat(token.isValid(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("isValid returns false when REVOKED")
    void isValid_returnsFalseWhenRevoked() {
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, FUTURE);
        token.revoke(Instant.now());

        assertThat(token.isValid(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when expired (expiresAt is in the past)")
    void isValid_returnsFalseWhenExpired() {
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, PAST);

        assertThat(token.isValid(Instant.now())).isFalse();
    }

    @Test
    @DisplayName("revoke sets status to REVOKED")
    void revoke_setsStatusRevoked() {
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, FUTURE);
        token.revoke(Instant.now());

        assertThat(token.getStatus()).isEqualTo(TokenStatus.REVOKED);
    }

    @Test
    @DisplayName("revoke sets revokedAt to the provided instant")
    void revoke_setsRevokedAt() {
        Instant now = Instant.now();
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, FUTURE);
        token.revoke(now);

        assertThat(token.getRevokedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("HashUtils.sha256 returns a deterministic 64-char lowercase hex string")
    void sha256_returnsDeterministicHexOf64Chars() {
        String hash1 = HashUtils.sha256(RAW_TOKEN);
        String hash2 = HashUtils.sha256(RAW_TOKEN);

        assertThat(hash1)
                .hasSize(64)
                .matches("[0-9a-f]+")
                .isEqualTo(hash2);
    }
}
