package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.integrations.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.integrations.application.query.ListConnectionsQuery;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lists an organization's integration connections (never exposing the token). */
@Component
@RequiredArgsConstructor
public class ListConnectionsQueryHandler {

    private final IntegrationConnectionRepository connections;

    @Transactional(readOnly = true)
    public List<IntegrationConnection> handle(ListConnectionsQuery query) {
        return connections.findAllByOrganizationId(query.organizationId());
    }
}
