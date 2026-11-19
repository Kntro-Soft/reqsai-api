-- US72: a story keeps every fragment of a meeting it comes from, so an analyst can check it against what
-- was agreed with the client. One row per quote: the session it was said in, the transcript segment where
-- it starts (null when the quote could not be located) and the verbatim text. Several rows per story:
-- one when it was generated or accepted from a suggestion, one more for each later suggestion that
-- refined it.
create table story_source_fragments
(
    id         uuid         not null default gen_random_uuid() primary key,
    story_id   uuid         not null references user_stories (id) on delete cascade,
    session_id uuid         not null,
    sequence   integer,
    quote      varchar(500) not null,

    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    created_by uuid,
    updated_by uuid
);

create index idx_story_source_fragments_story on story_source_fragments (story_id);

-- Stories that already remembered one fragment (user_stories.origin_*) keep it as their first source.
-- Stories generated in a session before that, with no quote, get no row: they are reported as
-- "origin not available".
insert into story_source_fragments (story_id, session_id, sequence, quote, created_at, updated_at)
select id, origin_session_id, origin_sequence, origin_quote, created_at, created_at
from user_stories
where origin_session_id is not null
  and origin_quote is not null;
