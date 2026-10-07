-- Global palette search: case- and accent-insensitive substring, word and fuzzy matching.
--
-- The first version matched with `col % term` only, i.e. whole-string trigram similarity >= 0.3. A short
-- query against a long label never reaches that threshold ("Costo" vs "Costo de delivery según la zona de
-- reparto" scores 0.16), and accents count as different trigrams ("Clinica" vs "Clínica"). The search
-- queries now compare normalized text (see public.search_normalize) with three index-backed predicates:
-- substring (LIKE), word similarity (<%) and the original whole-string similarity (%).
--
-- Pure SQL on purpose: lower() + translate() is IMMUTABLE, so it can back expression indexes, and it needs
-- no extra extension. The tenant migration with the same version builds the tenant-side indexes on top of
-- public.search_normalize, so changing its body later requires reindexing every search index.

CREATE OR REPLACE FUNCTION public.search_normalize(value text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$
    SELECT lower(translate(value,
        'ÁÀÂÄÃÅáàâäãåÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÖÕóòôöõÚÙÛÜúùûüÑñÇçÝýÿ',
        'aaaaaaaaaaaaeeeeeeeeiiiiiiiioooooooooouuuuuuuunnccyyy'))
$$;

-- '%<normalized term>%' with the LIKE wildcards of the term escaped, so "50%" or "snake_case" match
-- literally instead of matching everything.
CREATE OR REPLACE FUNCTION public.search_like_pattern(term text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$
    SELECT '%' || replace(replace(replace(public.search_normalize(term), E'\\', E'\\\\'), '%', E'\\%'), '_', E'\\_') || '%'
$$;

-- Relevance of a label for a term, higher is better. The integer part is the kind of match: 4 exact,
-- 3 the label starts with the term, 2 a word of the label starts with the term, 1 the term appears inside
-- a word, 0 fuzzy only (typo). The fraction (at most 0.5) orders labels of the same kind by trigram
-- similarity, so shorter and closer labels come first.
CREATE OR REPLACE FUNCTION public.search_score(label text, term text)
    RETURNS double precision
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$
    SELECT CASE
               WHEN n.label = n.term THEN 4
               WHEN starts_with(n.label, n.term) THEN 3
               WHEN strpos(' ' || regexp_replace(n.label, '[^[:alnum:]]+', ' ', 'g'), ' ' || n.term) > 0 THEN 2
               WHEN strpos(n.label, n.term) > 0 THEN 1
               ELSE 0
           END
           + (public.similarity(n.label, n.term) + public.word_similarity(n.term, n.label)) / 4
    FROM (SELECT public.search_normalize(label) AS label, public.search_normalize(term) AS term) AS n
$$;

DROP INDEX IF EXISTS public.idx_organizations_name_trgm;
DROP INDEX IF EXISTS public.idx_organizations_slug_trgm;
DROP INDEX IF EXISTS public.idx_members_display_name_trgm;
DROP INDEX IF EXISTS public.idx_members_email_trgm;

CREATE INDEX IF NOT EXISTS idx_organizations_name_search_trgm
    ON public.organizations USING gin (public.search_normalize(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_organizations_slug_search_trgm
    ON public.organizations USING gin (public.search_normalize(slug) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_members_display_name_search_trgm
    ON public.members USING gin (public.search_normalize(display_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_members_email_search_trgm
    ON public.members USING gin (public.search_normalize(email) gin_trgm_ops);
