package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.domain.port.out.RefreshTokenRepositoryPort;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Revokes a refresh token on sign-out.
 * <p>
 * This operation is idempotent: if the token is not found (already revoked, expired, or never issued)
 * the handler returns normally without throwing. The cookie will be cleared by the controller regardless.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RevokeRefreshTokenCommandHandler {

    private final RefreshTokenRepositoryPort refreshTokens;

    @Transactional
    public void handle(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String hash = HashUtils.sha256(rawRefreshToken);

        refreshTokens.findByTokenHash(hash).ifPresent(token -> {
            token.revoke(Instant.now());
            refreshTokens.save(token);
            log.info("Revoked refresh token for user {}", token.getUserId());
        });
    }
}
