package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.IssuedShareLink;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ShareLinkResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** Maps {@link ShareLink} to its response DTO; the raw token only travels with a freshly issued link. */
public final class ShareLinkResponseMapper {

    private ShareLinkResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ShareLinkResponse toResponse(IssuedShareLink issued) {
        return toResponse(issued.link(), issued.token());
    }

    public static ShareLinkResponse toResponse(ShareLink link) {
        return toResponse(link, null);
    }

    private static ShareLinkResponse toResponse(ShareLink link, @Nullable String token) {
        return new ShareLinkResponse(
                link.getId(),
                link.getProjectId(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.getRevokedAt(),
                link.isActive(Instant.now()),
                token);
    }
}
