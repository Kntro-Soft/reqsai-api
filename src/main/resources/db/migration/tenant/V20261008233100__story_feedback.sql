-- US50: what a client said about a story through a share link: an approval or a comment.
create table story_feedback
(
    id            uuid         primary key,
    story_id      uuid         not null,
    share_link_id uuid         not null,
    kind          varchar(16)  not null, -- StoryFeedbackKind enum: APPROVAL | COMMENT
    author_name   varchar(120) not null,
    comment       text,

    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    created_by    uuid,
    updated_by    uuid
);

create index idx_story_feedback_story on story_feedback (story_id, created_at);
