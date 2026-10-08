package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.RevokeShareLinkCommand;
import com.kntro.reqsai.discovery.application.port.ShareLinkRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Revokes a link of the current organization's project; a link of another project reads as unknown. */
@Component
@RequiredArgsConstructor
public class RevokeShareLinkCommandHandler {

    private final ShareLinkRepository links;

    @Transactional
    public ShareLink handle(RevokeShareLinkCommand command) {
        ShareLink link = links.findByIdAndProject(
                        command.linkId(), UUID.fromString(TenantContext.getCurrentTenant()), command.projectId())
                .orElseThrow(DiscoveryExceptions::shareLinkUnavailable);
        link.revoke(Instant.now());
        return links.save(link);
    }
}
