package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A link a client opens to review the project's stories")
public record ShareLinkResponse(

        @Schema(description = "Link identifier")
        UUID id,

        @Schema(description = "Project the link shows")
        UUID projectId,

        @Schema(description = "When the link was created")
        Instant createdAt,

        @Schema(description = "When the link stops working")
        Instant expiresAt,

        @Schema(description = "When the link was revoked, if it was", nullable = true)
        @Nullable Instant revokedAt,

        @Schema(description = "True while the link can be opened")
        boolean active,

        @Schema(description = "Raw token for the public URL /share/{token}. Only returned when the link is created; "
                + "it is not stored and cannot be read again", nullable = true)
        @Nullable String token
) {
}
