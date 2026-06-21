package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.UpdateProfileCommand;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UpdateProfileCommandHandler} with mocked ports.
 *
 * @see UpdateProfileCommandHandler
 */
@DisplayName("Application: Update Profile")
@ExtendWith(MockitoExtension.class)
class UpdateProfileCommandHandlerTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();

    @Mock
    private UserRepository users;

    @InjectMocks
    private UpdateProfileCommandHandler handler;

    @Test
    @DisplayName("should update first name, last name and avatar URL and return the updated user")
    void handle_updatesProfileAndReturnsUser() {
        // Arrange
        User user = new User(ACCOUNT_ID, "Old", "Name");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        UpdateProfileCommand command = new UpdateProfileCommand(
                user.getId(), "Jane", "Doe", "https://cdn.example.com/avatar.png");

        // Act
        User result = handler.handle(command);

        // Assert
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        verify(users).save(user);
    }

    @Test
    @DisplayName("should clear avatar URL when null is passed")
    void handle_clearsAvatarUrlWhenNull() {
        // Arrange
        User user = new User(ACCOUNT_ID, "Jane", "Doe");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        UpdateProfileCommand command = new UpdateProfileCommand(user.getId(), "Jane", "Doe", null);

        // Act
        User result = handler.handle(command);

        // Assert
        assertThat(result.getAvatarUrl()).isNull();
        verify(users).save(user);
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when user does not exist")
    void handle_throwsWhenUserNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(users.findById(unknownId)).thenReturn(Optional.empty());
        UpdateProfileCommand command = new UpdateProfileCommand(unknownId, "Jane", "Doe", null);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(EntityNotFoundException.class);
        verify(users, never()).save(any());
    }
}
