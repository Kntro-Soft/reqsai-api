CREATE TABLE glossary_terms (
    id          UUID         NOT NULL PRIMARY KEY,
    glossary_id UUID         NOT NULL REFERENCES glossaries(id) ON DELETE CASCADE,
    term        VARCHAR(200) NOT NULL,
    definition  VARCHAR(4000) NOT NULL,
    added_by    UUID         NOT NULL,
    added_at    TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_glossary_terms_glossary_id ON glossary_terms (glossary_id);
CREATE UNIQUE INDEX uq_glossary_terms_glossary_term_ci ON glossary_terms (glossary_id, lower(term));
