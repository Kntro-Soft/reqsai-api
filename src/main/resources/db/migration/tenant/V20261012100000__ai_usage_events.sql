-- Append-only ledger of the AI the organization used: one row per successful provider call, with the
-- tokens the provider reported (input, output, the cached part of the input) and the number that call
-- added to the monthly quota counter (quota_tokens). Speech-to-text rows carry audio_seconds instead
-- and never add to the quota. idempotency_key is the provider's call id (or a generated one), so a call
-- reported twice is stored once. project_id is a plain UUID: the ledger outlives the project.
create table ai_usage_events
(
    id                 uuid         primary key,
    project_id         uuid,
    feature            varchar(32)  not null, -- AiFeature enum
    model              varchar(100),
    input_tokens       bigint       not null default 0,
    output_tokens      bigint       not null default 0,
    cached_input_tokens bigint      not null default 0,
    quota_tokens       bigint       not null default 0,
    audio_seconds      bigint       not null default 0,
    idempotency_key    varchar(200) not null,
    created_at         timestamptz  not null,

    constraint uq_ai_usage_events_idempotency unique (idempotency_key)
);

create index idx_ai_usage_events_created on ai_usage_events (created_at);
create index idx_ai_usage_events_feature_created on ai_usage_events (feature, created_at);
create index idx_ai_usage_events_project_created on ai_usage_events (project_id, created_at);
