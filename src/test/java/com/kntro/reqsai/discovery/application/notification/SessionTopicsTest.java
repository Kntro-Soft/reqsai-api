package com.kntro.reqsai.discovery.application.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the centralized session destination strategy.
 *
 * @see SessionTopics
 */
@DisplayName("Realtime: SessionTopics")
class SessionTopicsTest {

    @Test
    @DisplayName("should build the logical session topic without a broker prefix")
    void should_build_session_topic() {
        // Arrange
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        // Act
        String topic = SessionTopics.of(sessionId);

        // Assert
        assertThat(topic).isEqualTo("sessions/11111111-1111-1111-1111-111111111111");
        assertThat(topic).doesNotStartWith("/topic"); // the notifier adds the prefix
    }

    @Test
    @DisplayName("should reject a null session id")
    void should_reject_null() {
        // Act & Assert
        assertThatThrownBy(() -> SessionTopics.of(null))
                .isInstanceOf(NullPointerException.class);
    }
}
