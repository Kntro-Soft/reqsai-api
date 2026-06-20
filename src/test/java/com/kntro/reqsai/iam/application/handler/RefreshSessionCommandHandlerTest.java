package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.IssuedToken;
import com.kntro.reqsai.iam.application.port.OrganizationLookupPort;
import com.kntro.reqsai.iam.application.port.TokenIssuer;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.result.RefreshedSession;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.domain.model.UserPreferences;
import com.kntro.reqsai.iam.application.port.RefreshTokenRepository;
import com.kntro.reqsai.shared.domain.exception.AuthenticationException;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RefreshSessionCommandHandler} with mocked ports.
 *
 * @see RefreshSessionCommandHandler
 */
@DisplayName("Application: Refresh Session")
@ExtendWith(MockitoExtension.class)
class RefreshSessionCommandHandlerTest {

    private static final String RAW_TOKEN = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private OrganizationLookupPort organizations;

    @Mock
    private UserRepository users;

    @Mock
    private AccountRepository accounts;

    @InjectMocks
    private RefreshSessionCommandHandler handler;

    @Test
    @DisplayName("should rotate the token and return a new access token")
    void handle_rotatesTokenAndReturnsNewAccessToken() {
        // Arrange
        RefreshToken existing = RefreshToken.issue(USER_ID, RAW_TOKEN, Instant.now().plusSeconds(3600));
        User user = new User(UUID.randomUUID(), "Test", "User");
        Account account = Account.register(Email.of("test@example.com"), "hash");
        when(refreshTokens.findByTokenHash(HashUtils.sha256(RAW_TOKEN)))
                .thenReturn(Optional.of(existing));
        when(users.findById(existing.getUserId())).thenReturn(Optional.of(user));
        when(accounts.findById(user.getAccountId())).thenReturn(Optional.of(account));
        when(organizations.findOrganizationIdByOwnerId(user.getId())).thenReturn(Optional.empty());
        when(tokenIssuer.issue(any(), any(), any(), any()))
                .thenReturn(new IssuedToken("access-jwt", 900L));

        // Act
        RefreshedSession result = handler.handle(RAW_TOKEN);

        // Assert
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.expiresInSeconds()).isEqualTo(900L);
        assertThat(result.rawRefreshToken()).isNotNull().isNotEqualTo(RAW_TOKEN);
        verify(refreshTokens, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("should throw AuthenticationException (INVALID_REFRESH_TOKEN) when token not found")
    void handle_throwsWhenTokenNotFound() {
        // Arrange
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(RAW_TOKEN))
                .isInstanceOf(AuthenticationException.class);
        verify(tokenIssuer, never()).issue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should throw AuthenticationException when token is expired")
    void handle_throwsWhenTokenExpired() {
        // Arrange — token was issued with an expiry in the past
        RefreshToken expired = RefreshToken.issue(USER_ID, RAW_TOKEN, Instant.now().minusSeconds(1));
        when(refreshTokens.findByTokenHash(HashUtils.sha256(RAW_TOKEN)))
                .thenReturn(Optional.of(expired));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(RAW_TOKEN))
                .isInstanceOf(AuthenticationException.class);
        verify(tokenIssuer, never()).issue(any(), any(), any(), any());
    }
}
