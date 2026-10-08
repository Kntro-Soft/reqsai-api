package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UpdateSessionSpeakerCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SessionSpeakerRepository;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import com.kntro.reqsai.discovery.domain.model.SpeakerSpan;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link UpdateSessionSpeakerCommandHandler} (US40). */
@DisplayName("Application: Update Session Speaker")
@ExtendWith(MockitoExtension.class)
class UpdateSessionSpeakerCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private SessionSpeakerRepository speakerRows;
    @Mock
    private SessionSpeakerService speakers;
    @InjectMocks
    private UpdateSessionSpeakerCommandHandler handler;

    private final DiscoverySession session = DiscoverySessionMother.draft().build();
    private final SpeakerRoster roster = SpeakerRoster.of(List.of(
            new SpeakerSpan("0", 0, 1_000), new SpeakerSpan("1", 1_000, 2_000), new SpeakerSpan("1", 2_000, 3_000)),
            List.of());

    @Test
    @DisplayName("describes a speaker for the first time, keeping their index and segment count")
    void describes_new_speaker() {
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(speakers.rosterOf(session.getId())).thenReturn(roster);
        when(speakerRows.findBySessionIdAndSpeakerLabel(session.getId(), "1")).thenReturn(Optional.empty());
        when(speakerRows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpeakerRoster.Speaker result = handler.handle(new UpdateSessionSpeakerCommand(
                session.getProjectId(), session.getId(), "1", " Luis ", SpeakerSide.TEAM));

        ArgumentCaptor<SessionSpeaker> saved = ArgumentCaptor.forClass(SessionSpeaker.class);
        verify(speakerRows).save(saved.capture());
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Luis");
        assertThat(saved.getValue().getSide()).isEqualTo(SpeakerSide.TEAM);
        assertThat(result.index()).isEqualTo(2);
        assertThat(result.name()).isEqualTo("Luis");
        assertThat(result.segmentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("updates the existing description of a speaker")
    void updates_existing_description() {
        SessionSpeaker existing = new SessionSpeaker(session.getId(), "0");
        existing.describe("Ana", SpeakerSide.TEAM);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(speakers.rosterOf(session.getId())).thenReturn(roster);
        when(speakerRows.findBySessionIdAndSpeakerLabel(session.getId(), "0")).thenReturn(Optional.of(existing));
        when(speakerRows.save(existing)).thenReturn(existing);

        SpeakerRoster.Speaker result = handler.handle(new UpdateSessionSpeakerCommand(
                session.getProjectId(), session.getId(), "0", "Ana Torres", SpeakerSide.CLIENT));

        assertThat(existing.getSide()).isEqualTo(SpeakerSide.CLIENT);
        assertThat(result.name()).isEqualTo("Ana Torres");
        assertThat(result.side()).isEqualTo(SpeakerSide.CLIENT);
    }

    @Test
    @DisplayName("answers SPEAKER_NOT_FOUND for a label that never spoke in the session")
    void rejects_unknown_label() {
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(speakers.rosterOf(session.getId())).thenReturn(roster);

        assertThatThrownBy(() -> handler.handle(new UpdateSessionSpeakerCommand(
                session.getProjectId(), session.getId(), "7", "X", null)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(e -> assertThat(((EntityNotFoundException) e).error())
                        .isEqualTo(DiscoveryError.SPEAKER_NOT_FOUND));
        verify(speakerRows, never()).save(any());
    }

    @Test
    @DisplayName("answers SESSION_NOT_FOUND when the session belongs to another project")
    void rejects_session_of_another_project() {
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> handler.handle(new UpdateSessionSpeakerCommand(
                UUID.randomUUID(), session.getId(), "0", "Ana", null)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
