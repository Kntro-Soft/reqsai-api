-- Lexical search primitive for the global palette (public schema / global registries).
-- Creates the pg_trgm extension in the public schema (mirrors how `vector` is created WITH SCHEMA
-- public) so the `%` similarity operator and `similarity()` ranking are available to every tenant via
-- search_path. Adds trigram GIN indexes on the palette-relevant columns of the global registries.

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE INDEX idx_organizations_name_trgm
    ON public.organizations USING gin (name gin_trgm_ops);

CREATE INDEX idx_organizations_slug_trgm
    ON public.organizations USING gin (slug gin_trgm_ops);

CREATE INDEX idx_members_display_name_trgm
    ON public.members USING gin (display_name gin_trgm_ops);

CREATE INDEX idx_members_email_trgm
    ON public.members USING gin (email gin_trgm_ops);
