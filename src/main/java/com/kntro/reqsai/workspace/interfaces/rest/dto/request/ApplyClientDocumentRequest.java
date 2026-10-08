package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.DocumentType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "The analyst's review of an analyzed client document: what to keep and what to add")
public record ApplyClientDocumentRequest(

        @Schema(description = "Document display name; omitted or blank keeps the file name",
                example = "Términos de referencia v2", maxLength = 255)
        @Size(max = 255)
        String name,

        @Schema(description = "Document classification within the project context",
                allowableValues = {"BUSINESS_RULES", "TECHNICAL_SPEC", "MEETING_NOTES", "GLOSSARY_SOURCE", "REFERENCE"},
                example = "TECHNICAL_SPEC", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        DocumentType documentType,

        @Schema(description = "Project context summary the AI reads in later sessions; blank stores none",
                maxLength = 4000)
        @Size(max = 4000)
        String summary,

        @ArraySchema(arraySchema = @Schema(description = "Glossary terms to add; ones already in the glossary are skipped"),
                maxItems = 100)
        @Size(max = 100)
        List<@Valid @NotNull GlossaryTermItem> glossaryTerms,

        @ArraySchema(arraySchema = @Schema(description = "Constraints to add; ones the project already records are skipped"),
                schema = @Schema(maxLength = 1000), maxItems = 100)
        @Size(max = 100)
        List<@NotBlank @Size(max = 1000) String> constraints
) {

    @Schema(description = "A glossary term selected from the analysis")
    public record GlossaryTermItem(
            @Schema(example = "Comensal", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200)
            String term,

            @Schema(example = "Cliente que reserva una mesa en el restaurante.", maxLength = 4000,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 4000)
            String definition
    ) {}
}
