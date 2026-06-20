package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ResendVerificationCommand;
import com.kntro.reqsai.iam.application.config.IamTokenProperties;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.EmailVerificationRepository;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResendVerificationCommandHandler} with mocked ports.
 *
 * @see ResendVerificationCommandHandler
 */
@DisplayName("Application: Resend Verification")
@ExtendWith(MockitoExtension.class)
class ResendVerificationCommandHandlerTest {

    private static final String EMAIL = "jane@example.com";

    @Mock private AccountRepository accounts;
    @Mock private EmailVerificationRepository emailVerifications;

    private ResendVerificationCommandHandler handler;

    @BeforeEach
    void setUp() {
        IamTokenProperties props = new IamTokenProperties(32, Duration.ofHours(1), Duration.ofHours(24));
        handler = new ResendVerificationCommandHandler(accounts, emailVerifications, props);
    }

    @Test
    @DisplayName("should issue a new token and resend verification email for a pending account")
    void handle_resendsVerificationForPendingAccount() {
        // Arrange
        Account account = Account.register(Email.of(EMAIL), "$2a$10$hash");
        when(accounts.findByEmail(Email.of(EMAIL))).thenReturn(Optional.of(account));

        // Act & Assert
        assertThatCode(() -> handler.handle(new ResendVerificationCommand(EMAIL)))
                .doesNotThrowAnyException();
        verify(emailVerifications).save(any());
    }

    @Test
    @DisplayName("should silently ignore when email is not registered (no enumeration)")
    void handle_silentlyIgnoresUnknownEmail() {
        // Arrange
        when(accounts.findByEmail(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatCode(() -> handler.handle(new ResendVerificationCommand(EMAIL)))
                .doesNotThrowAnyException();
        verify(emailVerifications, never()).save(any());
    }

    @Test
    @DisplayName("should silently ignore when account is already active (no enumeration)")
    void handle_silentlyIgnoresAlreadyActiveAccount() {
        // Arrange — account is ACTIVE (already verified)
        Account active = Account.register(Email.of(EMAIL), "$2a$10$hash");
        active.activate();
        when(accounts.findByEmail(Email.of(EMAIL))).thenReturn(Optional.of(active));

        // Act & Assert
        assertThatCode(() -> handler.handle(new ResendVerificationCommand(EMAIL)))
                .doesNotThrowAnyException();
        verify(emailVerifications, never()).save(any());
    }
}
