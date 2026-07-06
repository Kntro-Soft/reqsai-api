package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.query.ListPendingSuggestionsQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListPendingSuggestionsQueryHandler {

    private final SuggestionRepository suggestions;
    private final DiscoverySessionRepository sessions;

    @Transactional(readOnly = true)
    public List<Suggestion> handle(ListPendingSuggestionsQuery query) {
        sessions.findById(query.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));
        return suggestions.findAllBySessionIdAndStatus(query.sessionId(), query.statusOrDefault());
    }
}
