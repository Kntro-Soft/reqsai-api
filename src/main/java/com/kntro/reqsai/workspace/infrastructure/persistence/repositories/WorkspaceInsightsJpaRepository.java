package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.insights.ProjectRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Counting queries for the dashboards. Anchored on {@link Project} only to get
 * a JPA repository; they select projections and aggregates, never whole
 * entities beyond that anchor.
 */
public interface WorkspaceInsightsJpaRepository extends JpaRepository<Project, UUID> {

    @Query("""
            select new com.kntro.reqsai.workspace.insights.ProjectRef(p.id, p.name, p.demo)
            from Project p
            where p.organizationId = :organizationId and p.status = 'ACTIVE'
            order by lower(p.name)
            """)
    List<ProjectRef> activeProjects(@Param("organizationId") UUID organizationId);

    @Query("""
            select new com.kntro.reqsai.workspace.insights.ProjectRef(p.id, p.name, p.demo)
            from Project p
            where p.organizationId = :organizationId and p.status = 'ACTIVE' and p.id in :projectIds
            order by lower(p.name)
            """)
    List<ProjectRef> activeProjectsIn(@Param("organizationId") UUID organizationId,
            @Param("projectIds") Collection<UUID> projectIds);

    /**
     * Zero or one row:
     * {@code [glossaryTerms, constraints, documents, assignedMembers]}.
     */
    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select (select count(*) from glossary_terms t join glossaries g on g.id = t.glossary_id
                    where g.project_id = p.id),
                   (select count(*) from project_constraints c where c.project_id = p.id),
                   (select count(*) from project_documents d where d.project_id = p.id and d.status = 'ACTIVE'),
                   (select count(*) from project_members pm where pm.project_id = p.id)
            from projects p
            where p.id = :projectId
            """, nativeQuery = true)
    List<Object[]> knowledgeOf(@Param("projectId") UUID projectId);

    /** Row shape: {@code [role, status, count]}. */
    @Query("""
            select m.role, m.status, count(m)
            from Member m
            where m.organizationId = :organizationId
              and m.status in (com.kntro.reqsai.workspace.domain.model.MemberStatus.ACTIVE,
                               com.kntro.reqsai.workspace.domain.model.MemberStatus.PENDING)
              and (m.userId is null or m.userId <> :ownerId)
            group by m.role, m.status
            """)
    List<Object[]> memberCounts(@Param("organizationId") UUID organizationId, @Param("ownerId") UUID ownerId);
}
