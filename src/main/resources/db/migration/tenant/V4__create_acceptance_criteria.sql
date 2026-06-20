CREATE TABLE acceptance_criteria (
    id          UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    story_id    UUID          NOT NULL REFERENCES user_stories(id) ON DELETE CASCADE,
    scenario    VARCHAR(200),
    given_text  VARCHAR(1000) NOT NULL,
    when_text   VARCHAR(1000) NOT NULL,
    then_text   VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_acceptance_criteria_story_id ON acceptance_criteria(story_id);
