package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository.SessionStats;
import com.kntro.reqsai.discovery.application.query.GetProjectSessionQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Project Session")
@ExtendWith(MockitoExtension.class)
class GetProjectSessionQueryHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private SessionStatsRepository stats;
    @InjectMocks
    private GetProjectSessionQueryHandler handler;

    @Test
    @DisplayName("should return the session with its stats when it belongs to the given project")
    void should_return_session_for_correct_project() {
        UUID projectId = UUID.randomUUID();
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(stats.statsForSessions(java.util.List.of(session.getId())))
                .thenReturn(Map.of(session.getId(), new SessionStats(4, 3, 2, 1)));

        SessionWithStats result = handler.handle(new GetProjectSessionQuery(projectId, session.getId()));

        assertThat(result.session().getId()).isEqualTo(session.getId());
        assertThat(result.session().getProjectId()).isEqualTo(projectId);
        assertThat(result.stats().storiesGenerated()).isEqualTo(4);
        assertThat(result.stats().storiesAccepted()).isEqualTo(3);
        assertThat(result.stats().suggestionsPending()).isEqualTo(2);
        assertThat(result.stats().questionsAsked()).isEqualTo(1);
    }

    @Test
    @DisplayName("should throw 404 when the session does not exist")
    void should_throw_not_found_when_missing() {
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetProjectSessionQuery(projectId, sessionId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("should throw 404 when the session belongs to a different project")
    void should_throw_not_found_when_project_mismatch() {
        UUID ownerProjectId = UUID.randomUUID();
        UUID callerProjectId = UUID.randomUUID();
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(ownerProjectId).build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> handler.handle(new GetProjectSessionQuery(callerProjectId, session.getId())))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.SESSION_NOT_FOUND));
    }
}
