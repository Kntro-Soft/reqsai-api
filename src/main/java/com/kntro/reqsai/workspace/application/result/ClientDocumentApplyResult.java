package com.kntro.reqsai.workspace.application.result;

import com.kntro.reqsai.workspace.domain.model.ProjectDocument;

/**
 * Outcome of applying a reviewed client document.
 *
 * @param document              the document, now {@code ACTIVE}
 * @param glossaryTermsAdded    selected terms added to the glossary
 * @param glossaryTermsSkipped  selected terms skipped because the glossary already had them
 * @param constraintsAdded      selected constraints added to the project
 * @param constraintsSkipped    selected constraints skipped because the project already had them
 */
public record ClientDocumentApplyResult(
        ProjectDocument document,
        int glossaryTermsAdded,
        int glossaryTermsSkipped,
        int constraintsAdded,
        int constraintsSkipped
) {}
