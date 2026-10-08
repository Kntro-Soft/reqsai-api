package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UpdateSessionSpeakerCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SessionSpeakerRepository;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.SessionSpeaker;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Names a diarized speaker of a session and sets their side (US40). The speaker must have spoken in the
 * session; the description applies to all their segments, past and future, and to the speaker tags the AI
 * reads from then on. Raises {@code SessionSpeakerUpdatedEvent} so every viewer relabels the speaker live.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateSessionSpeakerCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final SessionSpeakerRepository speakerRows;
    private final SessionSpeakerService speakers;

    @Transactional
    public SpeakerRoster.Speaker handle(UpdateSessionSpeakerCommand command) {
        sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));
        SpeakerRoster roster = speakers.rosterOf(command.sessionId());
        SpeakerRoster.Speaker current = roster.find(command.speakerLabel())
                .orElseThrow(() -> DiscoveryExceptions.speakerNotFound(command.sessionId(), command.speakerLabel()));

        SessionSpeaker speaker = speakerRows.findBySessionIdAndSpeakerLabel(command.sessionId(), command.speakerLabel())
                .orElseGet(() -> new SessionSpeaker(command.sessionId(), command.speakerLabel()));
        speaker.describe(command.displayName(), command.side());
        SessionSpeaker saved = speakerRows.save(speaker);
        log.info("Speaker {} of session {} described (named: {}, side: {})",
                saved.getSpeakerLabel(), saved.getSessionId(), saved.getDisplayName() != null, saved.getSide());
        return new SpeakerRoster.Speaker(current.label(), current.index(), saved.getDisplayName(), saved.getSide(),
                current.segmentCount());
    }
}
