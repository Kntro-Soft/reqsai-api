package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.application.result.ClientDocumentAnalysis;
import com.kntro.reqsai.workspace.application.result.ClientDocumentApplyResult;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ClientDocumentAnalysisResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ClientDocumentApplyResponse;

public final class ClientDocumentResponseMapper {

    private ClientDocumentResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ClientDocumentAnalysisResponse toResponse(ClientDocumentAnalysis analysis) {
        ProjectDocument document = analysis.document();
        return new ClientDocumentAnalysisResponse(
                document.getId(),
                document.getFileName(),
                document.getMediaType(),
                document.getSizeBytes() == null ? 0 : document.getSizeBytes(),
                document.getExtractedChars() == null ? 0 : document.getExtractedChars(),
                analysis.truncated(),
                analysis.classified(),
                analysis.suggestedType().name(),
                analysis.summary() == null ? "" : analysis.summary(),
                analysis.glossary().stream()
                        .map(t -> new ClientDocumentAnalysisResponse.GlossaryItem(t.term(), t.definition(), t.exists()))
                        .toList(),
                analysis.constraints().stream()
                        .map(c -> new ClientDocumentAnalysisResponse.ConstraintItem(c.description(), c.exists()))
                        .toList());
    }

    public static ClientDocumentApplyResponse toResponse(ClientDocumentApplyResult result) {
        return new ClientDocumentApplyResponse(
                ProjectDocumentResponseMapper.toResponse(result.document()),
                result.glossaryTermsAdded(),
                result.glossaryTermsSkipped(),
                result.constraintsAdded(),
                result.constraintsSkipped());
    }
}
