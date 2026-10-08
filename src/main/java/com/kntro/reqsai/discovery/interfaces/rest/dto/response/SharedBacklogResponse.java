package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "What a client sees when opening a share link")
public record SharedBacklogResponse(

        @Schema(description = "Name of the project", example = "Restaurante La Tradición")
        String projectName,

        @Schema(description = "When the link stops working")
        Instant expiresAt,

        @Schema(description = "Stories under review, oldest first; rejected and merged stories are not shown")
        List<SharedStoryResponse> stories
) {
}
