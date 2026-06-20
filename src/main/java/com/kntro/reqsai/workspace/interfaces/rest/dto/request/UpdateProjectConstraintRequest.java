package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to replace a project constraint")
public record UpdateProjectConstraintRequest(

        @Schema(
                description = "Constraint or condition that must be respected in the project context",
                example = "Debe integrarse con SAP ECC",
                minLength = 1,
                maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 500)
        String description
) {}
