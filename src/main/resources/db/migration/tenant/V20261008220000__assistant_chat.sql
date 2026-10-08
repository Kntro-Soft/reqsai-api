-- Assistant chat: the analyst types to ReqsAI from the capture page, with or without a live session.
-- A requirement typed there becomes a suggestion that belongs to the project but to no session.
alter table suggestions alter column session_id drop not null;

create index idx_suggestions_project_status on suggestions (project_id, status);

create table assistant_messages
(
    id             uuid        primary key,
    project_id     uuid        not null,
    role           varchar(16) not null, -- AssistantMessageRole enum: ANALYST | ASSISTANT
    content        text        not null,
    suggestion_ids uuid[]      not null default '{}',

    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    created_by     uuid,
    updated_by     uuid
);

create index idx_assistant_messages_project_created on assistant_messages (project_id, created_at);
