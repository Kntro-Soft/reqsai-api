package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the {@link AssistantMessage} aggregate. */
@DisplayName("Domain: AssistantMessage")
class AssistantMessageTest {

    private static final UUID PROJECT = UUID.randomUUID();

    @Test
    @DisplayName("stores what the analyst typed, trimmed, with no suggestions")
    void analyst_message() {
        AssistantMessage message = AssistantMessage.fromAnalyst(PROJECT, "  ¿Cuántas historias hay?  ");

        assertThat(message.getRole()).isEqualTo(AssistantMessageRole.ANALYST);
        assertThat(message.getContent()).isEqualTo("¿Cuántas historias hay?");
        assertThat(message.getProjectId()).isEqualTo(PROJECT);
        assertThat(message.getSuggestionIds()).isEmpty();
    }

    @Test
    @DisplayName("rejects a blank or too long analyst message")
    void rejects_invalid_analyst_message() {
        assertThatThrownBy(() -> AssistantMessage.fromAnalyst(PROJECT, "   ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AssistantMessage.fromAnalyst(PROJECT, "x".repeat(AssistantMessage.ANALYST_MAX + 1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("links a reply to the suggestions it raised")
    void assistant_message_links_suggestions() {
        UUID suggestion = UUID.randomUUID();

        AssistantMessage message = AssistantMessage.fromAssistant(PROJECT, "Preparé una sugerencia.", List.of(suggestion));

        assertThat(message.getRole()).isEqualTo(AssistantMessageRole.ASSISTANT);
        assertThat(message.getSuggestionIds()).containsExactly(suggestion);
    }
}
