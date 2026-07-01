package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Trigram lexical search over the tenant {@code projects} table for the global palette. Native because
 * the {@code %} operator and {@code similarity()} come from pg_trgm; runs on the tenant connection so
 * {@code search_path} already targets the right schema. Returns {@code (id, name)} rows.
 */
public interface ProjectSearchJpaRepository extends JpaRepository<Project, UUID> {

    /** Owner/admin scope: every active project in the organization. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name
            from projects
            where organization_id = :organizationId
              and status = 'ACTIVE'
              and name % :term
            order by similarity(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("term") String term,
            Pageable pageable);

    /** Regular-member scope: only the caller's explicitly accessible projects. */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name
            from projects
            where organization_id = :organizationId
              and status = 'ACTIVE'
              and id in (:projectIds)
              and name % :term
            order by similarity(name, :term) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchByOrganizationAndIdIn(
            @Param("organizationId") UUID organizationId,
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("term") String term,
            Pageable pageable);
}
