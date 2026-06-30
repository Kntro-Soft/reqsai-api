package com.kntro.reqsai.shared.interfaces.rest;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AvatarUploads} — the validation guarding the avatar upload endpoints.
 *
 * @see AvatarUploads
 */
@DisplayName("Interfaces: Avatar upload validation")
class AvatarUploadsTest {

    private static final byte[] PNG_BYTES = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("should accept a valid PNG upload and return its bytes and content type")
    void validated_acceptsValidImage() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", PNG_BYTES);

        // Act
        GeneratedAvatar avatar = AvatarUploads.validated(file);

        // Assert
        assertThat(avatar.bytes()).isEqualTo(PNG_BYTES);
        assertThat(avatar.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("should normalize the content type (strip parameters, lowercase)")
    void validated_normalizesContentType() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "a.svg", "IMAGE/SVG+XML; charset=utf-8",
                "<svg/>".getBytes(StandardCharsets.UTF_8));

        // Act
        GeneratedAvatar avatar = AvatarUploads.validated(file);

        // Assert
        assertThat(avatar.contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    @DisplayName("should reject a non-image content type with 400")
    void validated_rejectsNonImage() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PNG_BYTES);

        // Act & Assert
        assertThatThrownBy(() -> AvatarUploads.validated(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject an oversized file with 400")
    void validated_rejectsOversized() {
        // Arrange
        byte[] tooBig = new byte[(int) AvatarUploads.MAX_BYTES + 1];
        MultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooBig);

        // Act & Assert
        assertThatThrownBy(() -> AvatarUploads.validated(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("should reject a missing/empty file with 400")
    void validated_rejectsEmpty() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        // Act & Assert
        assertThatThrownBy(() -> AvatarUploads.validated(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
