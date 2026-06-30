-- Make the project technical profile optional.
--
-- Project creation moves to a "name first" flow (the UI asks only for a name and
-- keeps the technical profile as an optional, advanced context). The LLM generation
-- prompt already tolerates a missing/empty profile, so only the persistence-level
-- NOT NULL constraints on the single-value text fields need to be relaxed.
--
-- The array columns stay NOT NULL: the domain normalizes a missing list to an
-- empty array ('{}'), never null, so no data ever violates the constraint.
ALTER TABLE projects
    ALTER COLUMN architecture DROP NOT NULL,
    ALTER COLUMN domain DROP NOT NULL;
