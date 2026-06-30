package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.UpdateUserAvatarCommand;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UpdateUserAvatarCommandHandler} with mocked ports.
 *
 * @see UpdateUserAvatarCommandHandler
 */
@DisplayName("Application: Update User Avatar")
@ExtendWith(MockitoExtension.class)
class UpdateUserAvatarCommandHandlerTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private UserRepository users;

    @InjectMocks
    private UpdateUserAvatarCommandHandler handler;

    @Test
    @DisplayName("should store the uploaded bytes and content type on the user")
    void handle_appliesAvatarAndReturnsUser() {
        // Arrange
        User user = new User(ACCOUNT_ID, "Jane", "Doe");
        byte[] bytes = "<svg/>".getBytes(StandardCharsets.UTF_8);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        UpdateUserAvatarCommand command = new UpdateUserAvatarCommand(user.getId(), bytes, "image/svg+xml");

        // Act
        User result = handler.handle(command);

        // Assert
        assertThat(result.getAvatar()).isEqualTo(bytes);
        assertThat(result.getAvatarContentType()).isEqualTo("image/svg+xml");
        verify(users).save(user);
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when user does not exist")
    void handle_throwsWhenUserNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(users.findById(unknownId)).thenReturn(Optional.empty());
        UpdateUserAvatarCommand command = new UpdateUserAvatarCommand(
                unknownId, "x".getBytes(StandardCharsets.UTF_8), "image/png");

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(EntityNotFoundException.class);
        verify(users, never()).save(any());
    }
}
