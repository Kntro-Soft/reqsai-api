package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body to transfer organization ownership to an existing active member")
public record TransferOwnershipRequest(
        @Schema(description = "Member id of the target active member who becomes the new owner",
                example = "019756a0-1234-7abc-8def-000000000010")
        @NotNull UUID newOwnerMemberId
) {}
