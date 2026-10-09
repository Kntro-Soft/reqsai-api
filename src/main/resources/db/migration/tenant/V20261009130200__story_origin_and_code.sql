-- Code-aware copilot: a story accepted from a suggestion remembers where it was said (session, segment
-- and quote) and the modules of the client's code it relates to.
alter table user_stories
    add column origin_session_id uuid,
    add column origin_sequence   integer,
    add column origin_quote      varchar(500),
    add column code_refs         jsonb not null default '[]'::jsonb;
