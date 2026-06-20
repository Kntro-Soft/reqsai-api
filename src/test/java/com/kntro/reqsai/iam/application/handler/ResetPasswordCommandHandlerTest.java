package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ResetPasswordCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.domain.model.Account;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResetPasswordCommandHandler} with mocked ports.
 *
 * @see ResetPasswordCommandHandler
 */
@DisplayName("Application: Reset Password")
@ExtendWith(MockitoExtension.class)
class ResetPasswordCommandHandlerTest {

    private static final String RAW_TOKEN = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    private static final String NEW_PASS  = "NewPass456!";
    private static final String NEW_HASH  = "$2a$10$hashed_new";

    @Mock private AccountRepository accounts;
    @Mock private PasswordHasher passwordHasher;

    @InjectMocks
    private ResetPasswordCommandHandler handler;

    @Test
    @DisplayName("should reset the password when token is valid and not expired")
    void handle_resetsPasswordSuccessfully() {
        // Arrange
        String tokenHash = HashUtils.sha256(RAW_TOKEN);
        Account account = Account.register(Email.of("user@example.com"), "$2a$10$old");
        account.generatePasswordResetToken(RAW_TOKEN, tokenHash, Instant.now().plusSeconds(3600));
        when(accounts.findByPasswordResetToken(tokenHash)).thenReturn(Optional.of(account));
        when(passwordHasher.hash(NEW_PASS)).thenReturn(NEW_HASH);

        // Act & Assert
        assertThatCode(() -> handler.handle(new ResetPasswordCommand(RAW_TOKEN, NEW_PASS)))
                .doesNotThrowAnyException();
        verify(accounts).save(account);
    }

    @Test
    @DisplayName("should throw AuthenticationException when token is not found")
    void handle_throwsWhenTokenNotFound() {
        // Arrange
        when(accounts.findByPasswordResetToken(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new ResetPasswordCommand(RAW_TOKEN, NEW_PASS)))
                .isInstanceOf(AuthenticationException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("should throw AuthenticationException when token is expired")
    void handle_throwsWhenTokenExpired() {
        // Arrange
        String tokenHash = HashUtils.sha256(RAW_TOKEN);
        Account account = Account.register(Email.of("user@example.com"), "$2a$10$old");
        // Token issued with expiry in the past
        account.generatePasswordResetToken(RAW_TOKEN, tokenHash, Instant.now().minusSeconds(1));
        when(accounts.findByPasswordResetToken(tokenHash)).thenReturn(Optional.of(account));
        when(passwordHasher.hash(NEW_PASS)).thenReturn(NEW_HASH);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new ResetPasswordCommand(RAW_TOKEN, NEW_PASS)))
                .isInstanceOf(AuthenticationException.class);
        verify(accounts, never()).save(any());
    }
}
