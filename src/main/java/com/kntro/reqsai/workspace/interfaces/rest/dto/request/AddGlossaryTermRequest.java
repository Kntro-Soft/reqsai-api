package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to add a glossary term to a project")
public record AddGlossaryTermRequest(

        @Schema(
                description = "Business term used in the client domain",
                example = "Lead",
                minLength = 1,
                maxLength = 200,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String term,

        @Schema(
                description = "Definition of the business term in the context of the project",
                example = "Potential customer who has shown interest but has not yet been qualified.",
                minLength = 1,
                maxLength = 4000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 4000)
        String definition
) {}
