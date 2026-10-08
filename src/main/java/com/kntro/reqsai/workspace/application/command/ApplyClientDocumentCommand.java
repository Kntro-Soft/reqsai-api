package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.DocumentType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The analyst's decision on an analyzed client document: keep it as project context (with the name,
 * type and summary they reviewed) and add the selected glossary terms and constraints.
 *
 * @param name          the document name, or {@code null} to keep the file name
 * @param summary       the context summary the AI will read, or {@code null}/blank for none
 * @param glossaryTerms the selected glossary terms; ones already in the glossary are skipped
 * @param constraints   the selected constraints; ones the project already records are skipped
 */
public record ApplyClientDocumentCommand(
        UUID organizationId,
        UUID projectId,
        UUID documentId,
        @Nullable String name,
        DocumentType documentType,
        @Nullable String summary,
        List<GlossaryTermInput> glossaryTerms,
        List<String> constraints,
        UUID requestedBy
) {

    public ApplyClientDocumentCommand {
        glossaryTerms = glossaryTerms == null ? List.of() : List.copyOf(glossaryTerms);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }

    public record GlossaryTermInput(String term, String definition) {}
}
