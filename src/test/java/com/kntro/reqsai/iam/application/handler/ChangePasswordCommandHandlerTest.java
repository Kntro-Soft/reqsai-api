package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.ChangePasswordCommand;
import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.PasswordHasher;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.shared.domain.exception.AuthenticationException;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangePasswordCommandHandler} with mocked ports.
 *
 * @see ChangePasswordCommandHandler
 */
@DisplayName("Application: Change Password")
@ExtendWith(MockitoExtension.class)
class ChangePasswordCommandHandlerTest {

    private static final String CURRENT_PASS = "OldPass123!";
    private static final String CURRENT_HASH = "$2a$10$hashed_current";
    private static final String NEW_PASS     = "NewPass456!";
    private static final String NEW_HASH     = "$2a$10$hashed_new";

    @Mock private UserRepository users;
    @Mock private AccountRepository accounts;
    @Mock private PasswordHasher passwordHasher;

    @InjectMocks
    private ChangePasswordCommandHandler handler;

    @Test
    @DisplayName("should change password when current password is correct")
    void handle_changesPasswordSuccessfully() {
        // Arrange
        Account account = Account.register(Email.of("user@example.com"), CURRENT_HASH);
        User user = new User(account.getId(), "Jane", "Doe");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(accounts.findById(user.getAccountId())).thenReturn(Optional.of(account));
        when(passwordHasher.matches(CURRENT_PASS, CURRENT_HASH)).thenReturn(true);
        when(passwordHasher.hash(NEW_PASS)).thenReturn(NEW_HASH);
        ChangePasswordCommand command = new ChangePasswordCommand(user.getId(), CURRENT_PASS, NEW_PASS);

        // Act & Assert
        assertThatCode(() -> handler.handle(command)).doesNotThrowAnyException();
        verify(accounts).save(account);
    }

    @Test
    @DisplayName("should throw AuthenticationException when current password is wrong")
    void handle_throwsWhenCurrentPasswordDoesNotMatch() {
        // Arrange
        Account account = Account.register(Email.of("user@example.com"), CURRENT_HASH);
        User user = new User(account.getId(), "Jane", "Doe");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(accounts.findById(user.getAccountId())).thenReturn(Optional.of(account));
        when(passwordHasher.matches(any(), any())).thenReturn(false);
        ChangePasswordCommand command = new ChangePasswordCommand(user.getId(), "wrong!", NEW_PASS);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(AuthenticationException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("should throw DomainException when new password is the same as current")
    void handle_throwsWhenNewPasswordMatchesCurrent() {
        // Arrange
        Account account = Account.register(Email.of("user@example.com"), CURRENT_HASH);
        User user = new User(account.getId(), "Jane", "Doe");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(accounts.findById(user.getAccountId())).thenReturn(Optional.of(account));
        when(passwordHasher.matches(CURRENT_PASS, CURRENT_HASH)).thenReturn(true);
        ChangePasswordCommand command = new ChangePasswordCommand(user.getId(), CURRENT_PASS, CURRENT_PASS);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("should throw when user is not found")
    void handle_throwsWhenUserNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(users.findById(unknownId)).thenReturn(Optional.empty());
        ChangePasswordCommand command = new ChangePasswordCommand(unknownId, CURRENT_PASS, NEW_PASS);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(RuntimeException.class);
        verify(accounts, never()).save(any());
    }
}
