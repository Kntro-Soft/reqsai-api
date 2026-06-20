package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.AuthenticateCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.IssuedToken;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.application.port.TokenIssuer;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.result.AuthenticatedSession;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.domain.port.out.RefreshTokenRepositoryPort;
import com.kntro.reqsai.iam.application.port.OrganizationLookupPort;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Authenticates an email + password and issues both an access token and a refresh token.
 * <p>
 * Flow: look up the account by email → verify the password against the stored hash → ensure the account
 * is active → load the linked user profile → issue a JWT access token (sub=userId, role=ROLE_USER) →
 * generate a cryptographically random refresh token, persist its SHA-256 hash, return the raw value so
 * the controller can set it as an HttpOnly cookie.
 * <p>
 * Bad email and bad password both surface as the same generic {@code InvalidCredentials} (401) so the
 * endpoint never reveals which accounts exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticateCommandHandler {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final long REFRESH_TOKEN_DAYS = 30L;

    private final AccountRepository accounts;
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final OrganizationLookupPort organizations;

    @Transactional
    public AuthenticatedSession handle(AuthenticateCommand command) {
        Email email = Email.of(command.email());

        Account account = accounts.findByEmail(email)
                .orElseThrow(IamExceptions::invalidCredentials);

        if (!passwordHasher.matches(command.password(), account.getPasswordHash())) {
            throw IamExceptions.invalidCredentials();
        }
        if (!account.isActive()) {
            throw IamExceptions.accountNotActive();
        }

        User user = users.findByAccountId(account.getId())
                .orElseThrow(() -> IamExceptions.userNotFound(account.getId()));

        // Prefer the user's last-visited org; fall back to the most recently created one.
        UUID orgId = resolveOrgId(user);

        IssuedToken token = tokenIssuer.issue(user.getId(), orgId, DEFAULT_ROLE, account.getTermsVersion());

        String rawRefreshToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS);
        RefreshToken refreshToken = RefreshToken.issue(user.getId(), rawRefreshToken, expiresAt);
        refreshTokens.save(refreshToken);

        log.info("Authenticated user {}", user.getId());
        return new AuthenticatedSession(token.token(), token.expiresInSeconds(), rawRefreshToken, user, orgId);
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
