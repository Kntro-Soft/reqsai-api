package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.query.ListPendingSuggestionsQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListPendingSuggestionsQueryHandler}: defaults to {@code PENDING} for the live
 * review queue (backward-compatible), and honors an explicit {@code status} so a past session's
 * decisions ({@code ACCEPTED} / {@code DISMISSED}) can be replayed.
 *
 * @see ListPendingSuggestionsQueryHandler
 */
@DisplayName("Application: List Session Suggestions by status")
@ExtendWith(MockitoExtension.class)
class ListPendingSuggestionsQueryHandlerTest {

    @Mock
    private SuggestionRepository suggestions;
    @Mock
    private DiscoverySessionRepository sessions;
    @InjectMocks
    private ListPendingSuggestionsQueryHandler handler;

    @Test
    @DisplayName("defaults to PENDING when no status is given")
    void should_default_to_pending() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        Suggestion s = Suggestion.newStory(sessionId, session.getProjectId(),
                "Login", "user", "log in", "access", Priority.HIGH, 3);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(suggestions.findAllBySessionIdAndStatus(eq(sessionId), eq(SuggestionStatus.PENDING)))
                .thenReturn(List.of(s));

        List<Suggestion> result = handler.handle(new ListPendingSuggestionsQuery(sessionId));

        assertThat(result).containsExactly(s);
    }

    @Test
    @DisplayName("honors an explicit ACCEPTED status filter")
    void should_honor_explicit_status() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(suggestions.findAllBySessionIdAndStatus(eq(sessionId), eq(SuggestionStatus.ACCEPTED)))
                .thenReturn(List.of());

        List<Suggestion> result = handler.handle(
                new ListPendingSuggestionsQuery(sessionId, SuggestionStatus.ACCEPTED));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("throws when the session does not exist")
    void should_throw_when_session_missing() {
        UUID missing = UUID.randomUUID();
        when(sessions.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ListPendingSuggestionsQuery(missing)))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(suggestions);
    }
}
