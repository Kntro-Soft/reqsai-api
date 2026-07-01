-- Lexical search primitive for the global palette (per-tenant schema).
-- Trigram (pg_trgm) GIN indexes back the `%` similarity operator and `similarity()` ranking used by
-- the search module's per-context ports. The pg_trgm extension itself is created once in the public
-- schema (see db/migration/common) and is visible here via the request's search_path.

CREATE INDEX idx_projects_name_trgm
    ON projects USING gin (name gin_trgm_ops);

CREATE INDEX idx_user_stories_title_trgm
    ON user_stories USING gin (title gin_trgm_ops);

CREATE INDEX idx_glossary_terms_term_trgm
    ON glossary_terms USING gin (term gin_trgm_ops);

CREATE INDEX idx_project_documents_name_trgm
    ON project_documents USING gin (name gin_trgm_ops);
