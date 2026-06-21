package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to replace project document metadata")
public record UpdateProjectDocumentRequest(
        @Schema(description = "Document display name", example = "Business Rules v2", minLength = 1, maxLength = 255)
        @NotBlank @Size(max = 255)
        String name,

        @Schema(
                description = "Document classification within the project context",
                allowableValues = {"BUSINESS_RULES", "TECHNICAL_SPEC", "MEETING_NOTES", "GLOSSARY_SOURCE", "REFERENCE"},
                example = "TECHNICAL_SPEC")
        @NotNull
        DocumentType documentType
) {}
