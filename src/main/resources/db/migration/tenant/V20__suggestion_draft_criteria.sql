-- Draft acceptance criteria for NEW_STORY suggestions.
--
-- The LLM already proposes structured Given/When/Then acceptance criteria for each generated story,
-- but the realtime suggestion path dropped them: a NEW_STORY was persisted with only its core
-- fields, so the story created on accept had no criteria. This column carries the proposed criteria
-- (a list, each { scenario?, given, when, then }) through the review gate so acceptance can create
-- the story WITH structured AcceptanceCriterion rows via the existing UserStory API.
--
-- Stored as JSONB so the structure survives round-trip (a plain-text column could not populate the
-- required given/when/then fields). Mapped in the domain entity as a List<DraftCriterion> via
-- Hibernate @JdbcTypeCode(SqlTypes.JSON), mirroring how UserStory maps its pgvector embedding.
-- EDGE_CASE / UPDATE_STORY / CLARIFYING_QUESTION leave it null.
alter table suggestions
    add column if not exists draft_criteria jsonb;
