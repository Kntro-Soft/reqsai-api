package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface GlossaryRepository {
    Glossary save(Glossary glossary);
    Optional<Glossary> findByProjectId(UUID projectId);

    /**
     * Paginated read of a project's glossary terms with an optional case-insensitive substring
     * {@code search} over term + definition ({@code null}/blank = no text filter). Server-side
     * pagination + filtering (scoped by project through the {@code glossaries} join), so paging and
     * total counts stay correct on large glossaries.
     */
    Page<GlossaryTerm> findTermsByProjectId(UUID projectId, @Nullable String search, Pageable pageable);
}
