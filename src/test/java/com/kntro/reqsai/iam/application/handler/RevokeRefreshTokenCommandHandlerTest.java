package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.domain.model.RefreshToken;
import com.kntro.reqsai.iam.domain.port.out.RefreshTokenRepositoryPort;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RevokeRefreshTokenCommandHandler} with a mocked repository.
 *
 * @see RevokeRefreshTokenCommandHandler
 */
@DisplayName("Application: Revoke Refresh Token")
@ExtendWith(MockitoExtension.class)
class RevokeRefreshTokenCommandHandlerTest {

    private static final String RAW_TOKEN = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepositoryPort refreshTokens;

    @InjectMocks
    private RevokeRefreshTokenCommandHandler handler;

    @Test
    @DisplayName("should revoke the token when found")
    void handle_revokesTokenSuccessfully() {
        // Arrange
        RefreshToken token = RefreshToken.issue(USER_ID, RAW_TOKEN, Instant.now().plusSeconds(3600));
        when(refreshTokens.findByTokenHash(HashUtils.sha256(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        // Act
        handler.handle(RAW_TOKEN);

        // Assert
        verify(refreshTokens).save(token);
        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("should be a no-op (no exception) when rawRefreshToken is null")
    void handle_isIdempotentWhenTokenNull() {
        // Act & Assert
        assertThatCode(() -> handler.handle(null))
                .doesNotThrowAnyException();
        verify(refreshTokens, never()).findByTokenHash(any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    @DisplayName("should be a no-op (no exception) when token not found in repository")
    void handle_isIdempotentWhenTokenNotFound() {
        // Arrange
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatCode(() -> handler.handle(RAW_TOKEN))
                .doesNotThrowAnyException();
        verify(refreshTokens, never()).save(any());
    }
}
