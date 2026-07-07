package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ForgotPasswordCommand;
import com.kntro.reqsai.iam.application.config.IamTokenProperties;
import com.kntro.reqsai.iam.application.port.AccountRepository;
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
 * Unit tests for {@link ForgotPasswordCommandHandler} with mocked ports.
 *
 * @see ForgotPasswordCommandHandler
 */
@DisplayName("Application: Forgot Password")
@ExtendWith(MockitoExtension.class)
class ForgotPasswordCommandHandlerTest {

    private static final String EMAIL = "jane@example.com";

    @Mock private AccountRepository accounts;

    private ForgotPasswordCommandHandler handler;

    @BeforeEach
    void setUp() {
        IamTokenProperties props = new IamTokenProperties(32, Duration.ofHours(1), Duration.ofHours(24));
        handler = new ForgotPasswordCommandHandler(accounts, props);
    }

    @Test
    @DisplayName("should issue a reset token and send email when account is active")
    void handle_sendsResetEmailForActiveAccount() {
        // Arrange
        Account account = Account.register(Email.of(EMAIL), "$2a$10$hash");
        account.activate();
        when(accounts.findByEmail(Email.of(EMAIL))).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenReturn(account);

        // Act & Assert
        assertThatCode(() -> handler.handle(new ForgotPasswordCommand(EMAIL)))
                .doesNotThrowAnyException();
        verify(accounts).save(account);
    }

    @Test
    @DisplayName("should silently ignore when email is not registered (no enumeration)")
    void handle_silentlyIgnoresUnknownEmail() {
        // Arrange
        when(accounts.findByEmail(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatCode(() -> handler.handle(new ForgotPasswordCommand(EMAIL)))
                .doesNotThrowAnyException();
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("should silently ignore when account is not active (no enumeration)")
    void handle_silentlyIgnoresInactiveAccount() {
        // Arrange — account is PENDING_VERIFICATION (not yet active)
        Account pending = Account.register(Email.of(EMAIL), "$2a$10$hash");
        when(accounts.findByEmail(Email.of(EMAIL))).thenReturn(Optional.of(pending));

        // Act & Assert
        assertThatCode(() -> handler.handle(new ForgotPasswordCommand(EMAIL)))
                .doesNotThrowAnyException();
        verify(accounts, never()).save(any());
    }
}
