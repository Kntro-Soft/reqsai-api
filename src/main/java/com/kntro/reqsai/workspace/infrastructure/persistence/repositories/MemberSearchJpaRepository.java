package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Trigram lexical search over the global {@code public.members} registry, scoped to a single
 * organization. Native for the pg_trgm {@code %} / {@code similarity()} functions.
 * Returns {@code (id, display_name, email)} rows.
 */
public interface MemberSearchJpaRepository extends JpaRepository<Member, UUID> {

    @SuppressWarnings("SqlResolve")
    @Query(value = """
            select id, display_name, email
            from public.members
            where organization_id = :organizationId
              and (display_name % :term or email % :term)
            order by greatest(similarity(display_name, :term), similarity(email, :term)) desc, display_name asc
            """, nativeQuery = true)
    List<Object[]> searchByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("term") String term,
            Pageable pageable);
}
