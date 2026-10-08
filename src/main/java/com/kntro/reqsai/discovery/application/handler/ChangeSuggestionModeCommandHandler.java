package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ChangeSuggestionModeCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Switches a session between automatic and on-demand analysis (US46). */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChangeSuggestionModeCommandHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional
    public DiscoverySession handle(ChangeSuggestionModeCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .filter(s -> s.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));
        session.changeSuggestionMode(command.mode());
        DiscoverySession saved = sessions.save(session);
        log.info("Discovery session {} of project {} now analyzes {}", saved.getId(), command.projectId(),
                command.mode());
        return saved;
    }
}
