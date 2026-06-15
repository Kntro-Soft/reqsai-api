package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.CreateDiscoverySessionCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@link DiscoverySession} in {@code DRAFT} for the current tenant. Persistence routes to the
 * tenant schema automatically (set per request from the JWT {@code orgId}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateDiscoverySessionCommandHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional
    public DiscoverySession handle(CreateDiscoverySessionCommand command) {
        DiscoverySession session = new DiscoverySession(command.projectId(), command.title(), LanguageCode.of(command.language()));
        DiscoverySession saved = sessions.save(session);
        log.info("Discovery session {} created for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
