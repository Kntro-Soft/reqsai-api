-- US71: quality indicators for the pilots.
--
-- discovery_sessions.purpose tells a discovery meeting from a clarification (follow-up) one, so the
-- indicator panel can count the clarification meetings instead of guessing them.
alter table discovery_sessions add column purpose varchar(16) not null default 'DISCOVERY';

-- user_stories.reviewed_at is the instant of the current review decision (approved or rejected); it is
-- cleared when the story goes back to draft. Stories decided before this migration keep it null: their
-- decision time was never recorded and is not guessed.
alter table user_stories add column reviewed_at timestamptz;

-- user_stories.generated_text is the text the AI proposed (title, role, action, benefit and criteria),
-- frozen when the story was generated or accepted from a suggestion. It is the baseline the final text is
-- compared with to tell an accepted story from a rewritten one. Manual, imported and earlier stories keep
-- it null and stay out of the acceptance rate.
alter table user_stories add column generated_text text;

create index idx_user_stories_project_reviewed on user_stories (project_id, reviewed_at) where reviewed_at is not null;
create index idx_discovery_sessions_project_ended on discovery_sessions (project_id, ended_at) where ended_at is not null;
