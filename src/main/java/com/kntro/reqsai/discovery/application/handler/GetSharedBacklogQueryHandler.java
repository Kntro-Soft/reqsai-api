package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.query.GetSharedBacklogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Opens a share link for an anonymous client: the project's stories under review and their feedback. */
@Component
@RequiredArgsConstructor
public class GetSharedBacklogQueryHandler {

    private final SharedLinkGateway gateway;
    private final SharedBacklogReader reader;

    public SharedBacklog handle(GetSharedBacklogQuery query) {
        return gateway.withActiveLink(query.token(), reader::read);
    }
}
