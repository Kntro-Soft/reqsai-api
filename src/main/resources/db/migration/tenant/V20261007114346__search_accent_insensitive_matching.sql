-- Global palette search: index the normalized labels (per-tenant schema).
--
-- The search queries now match public.search_normalize(col) (lower case, accents removed) with LIKE, <% and
-- %, so the trigram GIN indexes are rebuilt on that expression. The function is created in the public
-- schema by the common migration with the same version, which Spring Boot's Flyway applies before the
-- tenant migrations run. The raw-column indexes it replaces were only used by the search queries.

DROP INDEX IF EXISTS idx_projects_name_trgm;
DROP INDEX IF EXISTS idx_user_stories_title_trgm;
DROP INDEX IF EXISTS idx_glossary_terms_term_trgm;
DROP INDEX IF EXISTS idx_project_documents_name_trgm;

CREATE INDEX IF NOT EXISTS idx_projects_name_search_trgm
    ON projects USING gin (public.search_normalize(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_user_stories_title_search_trgm
    ON user_stories USING gin (public.search_normalize(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_glossary_terms_term_search_trgm
    ON glossary_terms USING gin (public.search_normalize(term) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_project_documents_name_search_trgm
    ON project_documents USING gin (public.search_normalize(name) gin_trgm_ops);
