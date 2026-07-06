package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Read port for paginated/searchable access to a project's constraints. Constraints are non-root
 * entities owned by {@code Project} (created/updated/deleted through the aggregate); this port exists
 * only for the server-side paginated <em>listing</em>, so it never mutates them.
 */
public interface ProjectConstraintRepository {

    /**
     * Paginated read of a project's constraints with an optional case-insensitive substring
     * {@code search} over description ({@code null}/blank = no text filter). Server-side pagination +
     * filtering, so paging and total counts stay correct on large constraint lists.
     */
    Page<ProjectConstraint> findByProjectId(UUID projectId, @Nullable String search, Pageable pageable);
}
