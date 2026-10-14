package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import com.kntro.reqsai.discovery.application.command.StartDiscoveryProcessingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.application.service.SpeakerTranscriptFormatter;
import com.kntro.reqsai.discovery.application.service.QuoteLocator;
import com.kntro.reqsai.discovery.application.service.StoryExtractionService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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

    /** Characters of the transcript used to look up related stories and code (a whole meeting is far longer). */
    private static final int CONTEXT_QUERY_CHARS = 6000;

    private final DiscoverySessionRepository sessions;
    private final RequirementGenerationPort requirementGeneration;
    private final StoryExtractionService storyExtraction;
    private final TranscriptSegmentRepository segments;
    private final SessionSpeakerService sessionSpeakers;
    private final RealtimeSuggestionService contextService;
    private final SuggestionRepository suggestions;
    private final AiQuotaGuard quota;

    @Transactional
    public ProcessingResult handle(StartDiscoveryProcessingCommand command) {
        quota.assertAvailable();
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
        String transcript = transcriptFor(session, finals);
        GenerationContext context = contextFor(session);
        var result = AiUsageScope.call(AiFeature.FINAL_STORIES, session.getProjectId(), () -> context == null
                ? requirementGeneration.generate(transcript, session.getLanguage().value())
                : requirementGeneration.generate(transcript, session.getLanguage().value(), context));
        // Each story keeps the fragment of the recording it was taken from (traceability).
        QuoteLocator locator = QuoteLocator.of(finals);
        return result.stories().stream()
                .map(gen -> storyExtraction.extractOne(gen, session.getId(), session.getProjectId(), locator))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The same context a live pass gets: backlog candidates, glossary and constraints, and the modules of the
     * connected code related to the conversation. Fails open: without it (no context, lookup failure) the stories
     * are generated from the transcript alone, as before.
     */
    private @Nullable GenerationContext contextFor(DiscoverySession session) {
        try {
            String text = session.getTranscript() == null ? "" : session.getTranscript();
            String query = text.length() <= CONTEXT_QUERY_CHARS ? text : text.substring(0, CONTEXT_QUERY_CHARS);
            List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(session.getId(),
                    SuggestionStatus.PENDING);
            return contextService.contextFor(session.getProjectId(), query, pending).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Session {}: stories will be generated without project context: {}", session.getId(),
                    e.getMessage());
            return null;
        }
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
