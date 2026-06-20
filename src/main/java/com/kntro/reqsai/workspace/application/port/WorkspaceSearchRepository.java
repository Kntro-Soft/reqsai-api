package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;

import java.util.List;
import java.util.UUID;

public interface WorkspaceSearchRepository {
    List<GlossaryTerm> findSimilarTerms(UUID projectId, float[] embedding, int topK);
    List<ProjectConstraint> findSimilarConstraints(UUID projectId, float[] embedding, int topK);
}
