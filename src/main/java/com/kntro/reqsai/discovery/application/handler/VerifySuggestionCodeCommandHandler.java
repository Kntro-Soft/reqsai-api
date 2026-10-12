package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.service.CodeVerificationService;
import com.kntro.reqsai.discovery.application.service.SuggestionCodeVerificationJob;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The analyst asks to verify a pending suggestion in the pure source code. The suggestion moves to
 * {@code VERIFYING} and the work runs in the background; the verdict arrives as an update of the card. Fails
 * with {@code CODE_REPOSITORY_NOT_READY} (409) when the project has no indexed repository to search.
 */
@Component
@RequiredArgsConstructor
public class VerifySuggestionCodeCommandHandler {

    /** A verification silent for this long (the server restarted) may be started again. */
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    private final SuggestionRepository suggestions;
    private final CodeVerificationService verification;
    private final SuggestionCodeVerificationJob job;

    @Transactional
    public Suggestion handle(UUID projectId, UUID suggestionId) {
        Suggestion suggestion = suggestions.findByIdAndProjectIdForUpdate(suggestionId, projectId)
                .orElseThrow(() -> DiscoveryExceptions.suggestionNotFound(suggestionId));
        if (!verification.hasReadyRepository(projectId)) {
            throw DiscoveryExceptions.codeRepositoryNotReady(projectId);
        }
        if (!verification.isAvailable()) {
            throw DiscoveryExceptions.codeVerificationFailed("the AI model is not available");
        }
        boolean start = suggestion.beginCodeVerification(Instant.now(), STALE_AFTER);
        Suggestion saved = suggestions.save(suggestion);
        if (start) {
            job.launch(projectId, suggestionId);
        }
        return saved;
    }
}
