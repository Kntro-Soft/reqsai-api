package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.CreateShareLinkCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.ShareLinkRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.shared.domain.support.TokenGenerator;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a share link for the current organization's project, or for one of its sessions. The link records the organization so
 * an anonymous visitor's request can be routed to the right tenant schema.
 */
@Component
@RequiredArgsConstructor
public class CreateShareLinkCommandHandler {

    static final int TOKEN_BYTES = 32;

    private final ShareLinkRepository links;
    private final DiscoverySessionRepository sessions;

    @Transactional
    public IssuedShareLink handle(CreateShareLinkCommand command) {
        UUID organizationId = UUID.fromString(TenantContext.getCurrentTenant());
        String token = TokenGenerator.generate(TOKEN_BYTES);
        ShareLink link = links.save(command.sessionId() == null
                ? ShareLink.issue(organizationId, command.projectId(), token, command.days(), Instant.now())
                : ShareLink.issueForSession(organizationId, command.projectId(), requireSessionOf(command), command.access(),
                        token, command.days(), Instant.now()));
        return new IssuedShareLink(link, token);
    }

    /** The session a link follows must exist and belong to the project the link is created under. */
    private java.util.UUID requireSessionOf(CreateShareLinkCommand command) {
        return sessions.findById(command.sessionId())
                .filter(session -> session.getProjectId().equals(command.projectId()))
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()))
                .getId();
    }
}
