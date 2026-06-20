package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to add a domain term to the project glossary")
public record AddGlossaryTermRequest(

        @Schema(description = "Domain term name", example = "Sprint", minLength = 1, maxLength = 150,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 150)
        String term,

        @Schema(description = "Plain-language definition of the term",
                example = "A fixed-length iteration (usually 2 weeks) in Scrum.",
                minLength = 1, maxLength = 2000,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 2000)
        String definition
) {}
