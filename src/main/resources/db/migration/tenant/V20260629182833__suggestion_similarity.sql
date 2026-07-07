-- Duplication alert similarity.
--
-- Cosine similarity (0..1) to the matched existing story for an UPDATE_STORY suggestion that was
-- raised because a batch-generated story closely matched one already in the backlog. Lets the UI
-- show "92% similar to <story>". NULL for ordinary (non-duplicate) suggestions.
alter table suggestions
    add column if not exists similarity double precision;
