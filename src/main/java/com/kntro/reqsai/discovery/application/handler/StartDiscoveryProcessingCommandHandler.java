package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StartDiscoveryProcessingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.application.service.SpeakerTranscriptFormatter;
import com.kntro.reqsai.discovery.application.service.QuoteLocator;
import com.kntro.reqsai.discovery.application.service.StoryExtractionService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the processing lifecycle of a {@link DiscoverySession}: validates preconditions,
 * drives the session state machine (PROCESSING → COMPLETED | FAILED), and delegates story
 * creation/persistence to {@link StoryExtractionService}.
 * <p>
 * Story persistence happens in per-story nested transactions (REQUIRES_NEW inside
 * {@code StoryExtractionService}), so WebSocket streaming events fire incrementally rather
 * than all at once when this outer transaction commits.
 * <p>
 * When the session's transcript is diarized (an uploaded recording whose provider labelled the speakers),
 * the model reads it as tagged speaker turns with the names and client/team sides the analyst gave
 * ({@link SpeakerTranscriptFormatter}), so it prioritizes what the client says (US40).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartDiscoveryProcessingCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final RequirementGenerationPort requirementGeneration;
    private final StoryExtractionService storyExtraction;
    private final TranscriptSegmentRepository segments;
    private final SessionSpeakerService sessionSpeakers;

    @Transactional
    public ProcessingResult handle(StartDiscoveryProcessingCommand command) {
        DiscoverySession session = loadAndValidate(command.sessionId());
        session.startProcessing();
        sessions.save(session);

        try {
            List<UserStory> created = generateAndPersistStories(session);
            return complete(session, created);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            return fail(session, e);
        }
    }

    private DiscoverySession loadAndValidate(UUID sessionId) {
        DiscoverySession session = sessions.findById(sessionId)
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(sessionId));
        if (session.getTranscript() == null || session.getTranscript().isBlank()) {
            throw DiscoveryExceptions.requirementGenerationFailed("Session has no transcript to process");
        }
        return session;
    }

    private List<UserStory> generateAndPersistStories(DiscoverySession session) {
        List<TranscriptSegment> finals = segments.findAllBySessionId(session.getId()).stream()
                .filter(TranscriptSegment::isFinal)
                .toList();
        var result = requirementGeneration.generate(transcriptFor(session, finals), session.getLanguage().value());
        // Each story keeps the fragment of the recording it was taken from (traceability).
        QuoteLocator locator = QuoteLocator.of(finals);
        return result.stories().stream()
                .map(gen -> storyExtraction.extractOne(gen, session.getId(), session.getProjectId(), locator))
                .flatMap(Optional::stream)
                .toList();
    }

    /** The diarized transcript as tagged speaker turns when its segments carry speakers, else the stored text. */
    private String transcriptFor(DiscoverySession session, List<TranscriptSegment> finals) {
        if (!SpeakerTranscriptFormatter.hasSpeakers(finals)) {
            return session.getTranscript();
        }
        return SpeakerTranscriptFormatter.format(finals, sessionSpeakers.rosterOf(session.getId()));
    }

    private ProcessingResult complete(DiscoverySession session, List<UserStory> created) {
        session.complete();
        sessions.save(session);
        log.info("Processing complete for session {}: {} stories created", session.getId(), created.size());
        return new ProcessingResult(session, created);
    }

    private ProcessingResult fail(DiscoverySession session, Exception e) {
        String reason = e.getMessage() != null ? e.getMessage() : "Unexpected error during generation";
        session.fail(reason);
        sessions.save(session);
        log.error("Processing failed for session {}: {}", session.getId(), reason, e);
        return new ProcessingResult(session, List.of());
    }

    public record ProcessingResult(DiscoverySession session, List<UserStory> stories) {}
}
