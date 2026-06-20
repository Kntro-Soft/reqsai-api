package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to create a project document metadata record")
public record CreateProjectDocumentRequest(
        @Schema(description = "Document display name", example = "Business Rules v1", minLength = 1, maxLength = 255)
        @NotBlank @Size(max = 255)
        String name,

        @Schema(
                description = "Document classification within the project context",
                allowableValues = {"BUSINESS_RULES", "TECHNICAL_SPEC", "MEETING_NOTES", "GLOSSARY_SOURCE", "REFERENCE"},
                example = "BUSINESS_RULES")
        @NotNull
        DocumentType documentType
) {}
