package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GlossaryTermSimilarityJpaRepository extends JpaRepository<GlossaryTerm, UUID> {

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            SELECT gt.* FROM glossary_terms gt
            JOIN glossaries g ON gt.glossary_id = g.id
            WHERE g.project_id = :projectId AND gt.embedding IS NOT NULL
            ORDER BY gt.embedding <=> CAST(:embedding AS vector)
            """, nativeQuery = true)
    List<GlossaryTerm> findSimilarByProjectId(
            @Param("projectId") UUID projectId,
            @Param("embedding") String embedding,
            Pageable pageable
    );
}
