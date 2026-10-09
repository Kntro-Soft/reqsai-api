-- Code-aware copilot: every suggestion keeps the verbatim quote it came from (and the transcript
-- segment that holds it), and what the client's code says about it: already built, or in conflict.
alter table suggestions
    add column evidence_sequence integer,
    add column evidence_quote    varchar(500),
    add column code_finding      varchar(32), -- CodeFinding enum: ALREADY_EXISTS | CONFLICTS_WITH_CODE
    add column code_note         varchar(1000),
    add column code_refs         jsonb not null default '[]'::jsonb;
