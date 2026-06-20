package com.kntro.reqsai.iam.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Navigation preferences to persist for the authenticated user")
public record UpdatePreferencesRequest(

        @Nullable
        @Schema(description = "ID of the organization to activate. Send null to clear.", nullable = true,
                example = "019756a0-1234-7abc-8def-000000000099")
        UUID lastVisitedOrgId
) {
}
