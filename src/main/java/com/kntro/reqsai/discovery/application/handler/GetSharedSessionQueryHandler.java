package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.query.GetSharedSessionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Opens a session share link for an anonymous visitor: the session, its transcript, suggestions and stories. */
@Component
@RequiredArgsConstructor
public class GetSharedSessionQueryHandler {

    private final SharedLinkGateway gateway;
    private final SharedSessionReader reader;

    public SharedSession handle(GetSharedSessionQuery query) {
        return gateway.withActiveSessionLink(query.token(), reader::read);
    }
}
