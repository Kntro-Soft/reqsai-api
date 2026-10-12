package com.kntro.reqsai.discovery.domain.event;

import com.kntro.reqsai.discovery.domain.model.CodeFinding;
import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import com.kntro.reqsai.discovery.domain.model.CodeVerifyState;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Raised when the deep verification of a suggestion against the code starts, finishes or fails. Carries what the
 * card shows (state, verdict, finding, note and references), so the live card updates without a fetch.
 */
public record SuggestionCodeVerifiedEvent(
        UUID suggestionId,
        @Nullable UUID sessionId,
        UUID projectId,
        CodeVerifyState state,
        @Nullable CodeVerdict verdict,
        @Nullable Instant verifiedAt,
        @Nullable String error,
        @Nullable CodeFinding codeFinding,
        @Nullable String codeNote,
        List<CodeReference> codeReferences,
        Instant occurredAt
) implements DomainEvent {

    public static SuggestionCodeVerifiedEvent of(Suggestion s) {
        return new SuggestionCodeVerifiedEvent(
                s.getId(), s.getSessionId(), s.getProjectId(), s.getCodeVerifyState(), s.getCodeVerdict(),
                s.getCodeVerifiedAt(), s.getCodeVerifyError(), s.getCodeFinding(), s.getCodeNote(),
                List.copyOf(s.getCodeReferences()), Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return suggestionId;
    }
}
