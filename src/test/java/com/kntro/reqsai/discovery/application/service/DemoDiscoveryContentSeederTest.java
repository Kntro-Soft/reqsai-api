package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.ProjectDiscoveryDataRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: Demo discovery content seeder")
@ExtendWith(MockitoExtension.class)
class DemoDiscoveryContentSeederTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private TranscriptSegmentRepository segments;
    @Mock
    private UserStoryRepository stories;
    @Mock
    private SuggestionRepository suggestions;
    @Mock
    private ProjectDiscoveryDataRepository projectData;
    @InjectMocks
    private DemoDiscoveryContentSeeder seeder;

    @Test
    @DisplayName("wipes the project's discovery data, then saves the sample content")
    void wipes_then_seeds() {
        when(sessions.findActiveByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        seeder.reseed(PROJECT_ID);

        InOrder order = inOrder(projectData, sessions);
        order.verify(projectData).deleteAllByProjectId(PROJECT_ID);
        ArgumentCaptor<DiscoverySession> session = ArgumentCaptor.forClass(DiscoverySession.class);
        order.verify(sessions).save(session.capture());
        assertThat(session.getValue().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(segments, times(DemoDiscoveryContent.segmentCount())).save(any());
        verify(stories, times(DemoDiscoveryContent.storyCount())).save(any());
        verify(suggestions, times(2)).save(any());
    }

    @Test
    @DisplayName("refuses while a session of the project is live, touching nothing")
    void refuses_while_a_session_is_live() {
        DiscoverySession live = new DiscoverySession(PROJECT_ID, "En vivo", DemoDiscoveryContent.LANGUAGE);
        live.startRecording(Instant.now());
        when(sessions.findActiveByProjectId(PROJECT_ID)).thenReturn(Optional.of(live));

        assertThatThrownBy(() -> seeder.reseed(PROJECT_ID))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(DiscoveryError.SESSION_ALREADY_ACTIVE);
        verifyNoInteractions(projectData, segments, stories, suggestions);
        verify(sessions, never()).save(any());
    }
}
