package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Paginated lexical read over the tenant {@code project_constraints} table for the Restricciones page.
 * JPQL (not native) so it returns a proper {@code Page<ProjectConstraint>} with an accurate total
 * count; scoped by project through the {@code project} relation. Complements
 * {@link ProjectConstraintSimilarityJpaRepository} (vector retrieval for RAG), which this does not
 * touch.
 */
public interface ProjectConstraintSearchJpaRepository extends JpaRepository<ProjectConstraint, UUID> {

    /**
     * Paginated constraints of a single project with an optional case-insensitive substring
     * {@code search} over description. The {@code :search} is null-guarded so a {@code null}/blank
     * argument disables the filter and returns the whole (paginated) constraint list. Sorting/paging
     * come from the {@link Pageable}.
     */
    @Query("""
            select c from ProjectConstraint c
            where c.project.id = :projectId
              and (cast(:search as string) is null
                   or lower(c.description) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<ProjectConstraint> findPageByProjectId(
            @Param("projectId") UUID projectId,
            @Param("search") @Nullable String search,
            Pageable pageable);
}
