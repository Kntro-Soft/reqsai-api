package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.domain.model.CodeVerifyState;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The short transactions around a background deep verification: reading what to verify, and storing the
 * verdict (or the failure) in the suggestion, which publishes the event the live card is updated from. A
 * suggestion decided meanwhile, or whose verification was superseded, is left alone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuggestionCodeVerificationWriter {

    private static final String DEFAULT_LANGUAGE = "es";

    private final SuggestionRepository suggestions;
    private final DiscoverySessionRepository sessions;

    /** What to verify and in which language, when the suggestion is still waiting for its verification. */
    public record Target(String subject, String language) {
    }

    @Transactional(readOnly = true)
    public Optional<Target> load(UUID projectId, UUID suggestionId) {
        return suggestions.findById(suggestionId)
                .filter(s -> s.getProjectId().equals(projectId) && s.getStatus() == SuggestionStatus.PENDING
                        && s.getCodeVerifyState() == CodeVerifyState.VERIFYING)
                .map(s -> new Target(SuggestionCodeSubject.of(s), languageOf(s)));
    }

    @Transactional
    public void complete(UUID projectId, UUID suggestionId, CodeVerification result) {
        lockWaiting(projectId, suggestionId).ifPresent(s -> {
            s.completeCodeVerification(result.verdict(), result.note(), result.references(), Instant.now());
            suggestions.save(s);
        });
    }

    @Transactional
    public void fail(UUID projectId, UUID suggestionId, String reason) {
        lockWaiting(projectId, suggestionId).ifPresent(s -> {
            s.failCodeVerification(reason);
            suggestions.save(s);
        });
    }

    private Optional<Suggestion> lockWaiting(UUID projectId, UUID suggestionId) {
        return suggestions.findByIdAndProjectIdForUpdate(suggestionId, projectId)
                .filter(s -> s.getStatus() == SuggestionStatus.PENDING
                        && s.getCodeVerifyState() == CodeVerifyState.VERIFYING);
    }

    private String languageOf(Suggestion s) {
        if (s.getSessionId() == null) return DEFAULT_LANGUAGE;
        return sessions.findById(s.getSessionId()).map(d -> d.getLanguage().value()).orElse(DEFAULT_LANGUAGE);
    }
}
