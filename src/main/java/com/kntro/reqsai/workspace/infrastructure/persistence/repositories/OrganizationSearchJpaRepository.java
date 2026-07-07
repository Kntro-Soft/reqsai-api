package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Organization;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Trigram lexical search over the global {@code public.organizations} registry, scoped to the
 * organizations the caller belongs to. Native for the pg_trgm {@code %} / {@code similarity()} functions.
 * Returns {@code (id, name, slug)} rows.
 */
public interface OrganizationSearchJpaRepository extends JpaRepository<Organization, UUID> {

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, name, slug
            from public.organizations
            where id in (:organizationIds)
              and (name % :term or slug % :term)
            order by greatest(similarity(name, :term), similarity(slug, :term)) desc, name asc
            """, nativeQuery = true)
    List<Object[]> searchWithinIds(
            @Param("organizationIds") Collection<UUID> organizationIds,
            @Param("term") String term,
            Pageable pageable);
}
