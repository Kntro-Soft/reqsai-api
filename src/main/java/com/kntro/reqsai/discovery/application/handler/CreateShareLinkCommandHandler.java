package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.CreateShareLinkCommand;
import com.kntro.reqsai.discovery.application.port.ShareLinkRepository;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.shared.domain.support.TokenGenerator;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a share link for the current organization's project. The link records the organization so
 * an anonymous visitor's request can be routed to the right tenant schema.
 */
@Component
@RequiredArgsConstructor
public class CreateShareLinkCommandHandler {

    static final int TOKEN_BYTES = 32;

    private final ShareLinkRepository links;

    @Transactional
    public IssuedShareLink handle(CreateShareLinkCommand command) {
        UUID organizationId = UUID.fromString(TenantContext.getCurrentTenant());
        String token = TokenGenerator.generate(TOKEN_BYTES);
        ShareLink link = links.save(
                ShareLink.issue(organizationId, command.projectId(), token, command.days(), Instant.now()));
        return new IssuedShareLink(link, token);
    }
}
