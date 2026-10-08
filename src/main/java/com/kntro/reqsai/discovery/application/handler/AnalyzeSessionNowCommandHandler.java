package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AnalyzeSessionNowCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Analizar ahora" (US46): runs a suggestion pass over the conversation accrued since the last one,
 * whatever the session's mode. The suggestions reach the review tray through the usual realtime
 * notifications; the handler returns how many were raised.
 */
@Component
@RequiredArgsConstructor
public class AnalyzeSessionNowCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final RealtimeSuggestionService suggestionService;

    public int handle(AnalyzeSessionNowCommand command) {
        sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));
        return suggestionService.analyzeNow(command.sessionId());
    }
}
