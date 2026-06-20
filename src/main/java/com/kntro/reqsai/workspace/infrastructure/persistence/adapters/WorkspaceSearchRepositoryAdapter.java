package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.WorkspaceSearchRepository;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.GlossaryTermSimilarityJpaRepository;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectConstraintSimilarityJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkspaceSearchRepositoryAdapter implements WorkspaceSearchRepository {

    private final GlossaryTermSimilarityJpaRepository termRepo;
    private final ProjectConstraintSimilarityJpaRepository constraintRepo;

    @Override
    public List<GlossaryTerm> findSimilarTerms(UUID projectId, float[] embedding, int topK) {
        return termRepo.findSimilarByProjectId(projectId, toVectorLiteral(embedding), PageRequest.of(0, topK));
    }

    @Override
    public List<ProjectConstraint> findSimilarConstraints(UUID projectId, float[] embedding, int topK) {
        return constraintRepo.findSimilarByProjectId(projectId, toVectorLiteral(embedding), PageRequest.of(0, topK));
    }

    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
