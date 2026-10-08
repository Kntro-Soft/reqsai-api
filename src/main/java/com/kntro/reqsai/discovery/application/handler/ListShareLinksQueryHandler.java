package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.ShareLinkRepository;
import com.kntro.reqsai.discovery.application.query.ListShareLinksQuery;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListShareLinksQueryHandler {

    private final ShareLinkRepository links;

    @Transactional(readOnly = true)
    public List<ShareLink> handle(ListShareLinksQuery query) {
        return links.findAllByProject(UUID.fromString(TenantContext.getCurrentTenant()), query.projectId());
    }
}
