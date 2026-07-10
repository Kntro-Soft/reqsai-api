package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.query.ListConnectionsQuery;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
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
