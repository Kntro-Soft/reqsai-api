package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to add a constraint to the project")
public record AddProjectConstraintRequest(

        @Schema(description = "Constraint description",
                example = "Must comply with PCI-DSS for all payment operations.",
                minLength = 1, maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000)
        String description
) {}
