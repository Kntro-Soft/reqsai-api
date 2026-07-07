package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.DismissSuggestionCommand;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
}
