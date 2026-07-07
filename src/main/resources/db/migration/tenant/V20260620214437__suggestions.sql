-- Suggestion review layer: AI-generated proposals pending analyst acceptance before
-- they are committed to the backlog (user_stories table).

create table suggestions
(
    id               uuid                     primary key,
    session_id       uuid                     not null,
    project_id       uuid                     not null,
    type             varchar(32)              not null, -- SuggestionType enum
    status           varchar(32)              not null, -- SuggestionStatus enum

    -- Draft story payload (NEW_STORY, UPDATE_STORY, EDGE_CASE)
    draft_title      varchar(200),
    draft_role       varchar(500),
    draft_action     varchar(500),
    draft_benefit    varchar(500),
    draft_priority   varchar(32),
    draft_story_points integer,

    -- Edge-case hint for target story resolution
    related_topic    varchar(500),

    -- Target story for UPDATE_STORY and EDGE_CASE
    target_story_id  uuid,

    -- Clarifying question payload
    question         varchar(1000),

    -- Populated after acceptance
    resolved_story_id uuid,

    created_at       timestamptz              not null,
    updated_at       timestamptz              not null,
    created_by       uuid,
    updated_by       uuid
);

-- Filter pending suggestions for a session in one index scan
create index idx_suggestions_session_status on suggestions (session_id, status);
-- Allow quick lookup of all suggestions for a project
create index idx_suggestions_project_id on suggestions (project_id);
