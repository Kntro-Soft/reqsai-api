package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.query.GetProjectSessionQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectSessionQueryHandler {

    private final DiscoverySessionRepository sessions;

    @Transactional(readOnly = true)
    public DiscoverySession handle(GetProjectSessionQuery query) {
        DiscoverySession session = sessions.findById(query.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));
        if (!session.getProjectId().equals(query.projectId())) {
            throw DiscoveryExceptions.sessionNotFound(query.sessionId());
        }
        return session;
    }
}
