package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.DismissSuggestionCommand;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DismissSuggestionCommandHandler {

    private final SuggestionRepository suggestions;

    @Transactional
    public Suggestion handle(DismissSuggestionCommand cmd) {
        Suggestion suggestion = suggestions.findByIdAndSessionIdForUpdate(cmd.suggestionId(), cmd.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.suggestionNotFound(cmd.suggestionId()));
        suggestion.dismiss();
        return suggestions.save(suggestion);
    }

    /** Dismisses a suggestion looked up by its project — the entry point for assistant-chat suggestions. */
    @Transactional
    public Suggestion handleInProject(UUID projectId, UUID suggestionId) {
        Suggestion suggestion = suggestions.findByIdAndProjectIdForUpdate(suggestionId, projectId)
                .orElseThrow(() -> DiscoveryExceptions.suggestionNotFound(suggestionId));
        suggestion.dismiss();
        return suggestions.save(suggestion);
    }
}
