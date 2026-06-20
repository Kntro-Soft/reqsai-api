package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.IssuedToken;
import com.kntro.reqsai.iam.application.port.OrganizationLookupPort;
import com.kntro.reqsai.iam.application.port.TokenIssuer;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.result.RefreshedSession;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.domain.port.out.RefreshTokenRepositoryPort;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Rotates a refresh token and issues a new access token.
 * <p>
 * Flow: hash the raw cookie value → find the stored token → validate it → revoke it → issue a new
 * refresh token → load user + account to resolve orgId and termsVersion → issue a new access token
 * → return both to the controller.
 * <p>
 * If the token is not found or is no longer valid, {@code INVALID_REFRESH_TOKEN} (401) is thrown.
 * Rotation is atomic: the old token is revoked in the same transaction that persists the new one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshSessionCommandHandler {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final long REFRESH_TOKEN_DAYS = 30L;

    private final RefreshTokenRepositoryPort refreshTokens;
    private final TokenIssuer tokenIssuer;
    private final OrganizationLookupPort organizations;
    private final UserRepository users;
    private final AccountRepository accounts;

    @Transactional
    public RefreshedSession handle(String rawRefreshToken) {
        String hash = HashUtils.sha256(rawRefreshToken);

        RefreshToken existing = refreshTokens.findByTokenHash(hash)
                .orElseThrow(IamExceptions::invalidRefreshToken);

        Instant now = Instant.now();
        if (!existing.isValid(now)) {
            throw IamExceptions.invalidRefreshToken();
        }

        existing.revoke(now);
        refreshTokens.save(existing);

        String newRawToken = generateRawToken();
        Instant expiresAt = now.plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS);
        RefreshToken newToken = RefreshToken.issue(existing.getUserId(), newRawToken, expiresAt);
        refreshTokens.save(newToken);

        // Load user to resolve org preference; load account for current termsVersion claim.
        User user = users.findById(existing.getUserId())
                .orElseThrow(() -> IamExceptions.userNotFound(existing.getUserId()));
        UUID orgId = resolveOrgId(user);

        Account account = accounts.findById(user.getAccountId())
                .orElseThrow(() -> IamExceptions.userNotFound(user.getAccountId()));

        IssuedToken accessToken = tokenIssuer.issue(existing.getUserId(), orgId, DEFAULT_ROLE, account.getTermsVersion());

        log.info("Rotated refresh token for user {}", existing.getUserId());
        return new RefreshedSession(accessToken.token(), accessToken.expiresInSeconds(), newRawToken, orgId);
    }

    private UUID resolveOrgId(User user) {
        var prefs = user.getPreferences();
        if (prefs != null && prefs.lastVisitedOrgId() != null) {
            return prefs.lastVisitedOrgId();
        }
        return organizations.findOrganizationIdByOwnerId(user.getId()).orElse(null);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(REFRESH_TOKEN_BYTES * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
