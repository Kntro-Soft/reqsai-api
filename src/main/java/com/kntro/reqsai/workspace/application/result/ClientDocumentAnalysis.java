package com.kntro.reqsai.workspace.application.result;

import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the analyst reviews after uploading a client document.
 *
 * @param document      the stored document, still {@code PENDING}
 * @param classified    whether the AI classified the text; when not, the lists are empty and the
 *                      summary is an excerpt of the document
 * @param truncated     whether the text was longer than the extraction cap and was cut
 * @param summary       the proposed project context
 * @param suggestedType the proposed document type
 * @param glossary      the proposed glossary terms, flagged when the glossary already has them
 * @param constraints   the proposed constraints, flagged when the project already records them
 */
public record ClientDocumentAnalysis(
        ProjectDocument document,
        boolean classified,
        boolean truncated,
        @Nullable String summary,
        DocumentType suggestedType,
        List<GlossarySuggestion> glossary,
        List<ConstraintSuggestion> constraints
) {

    public record GlossarySuggestion(String term, String definition, boolean exists) {}

    public record ConstraintSuggestion(String description, boolean exists) {}
}
