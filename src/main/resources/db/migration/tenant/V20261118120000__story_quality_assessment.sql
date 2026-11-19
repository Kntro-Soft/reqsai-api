-- US68: the latest quality assessment of a story: overall score, one finding per characteristic.
create table story_quality_assessments
(
    id           uuid        primary key,
    story_id     uuid        not null references user_stories (id) on delete cascade,
    score        integer     not null check (score between 0 and 100),
    findings     jsonb       not null, -- list of {characteristic, score, observation, suggestion}
    evaluated_at timestamptz not null,

    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    created_by   uuid,
    updated_by   uuid
);

create unique index uq_story_quality_assessments_story on story_quality_assessments (story_id);
