-- Code-aware copilot: repositories are read through the organization's GitHub App installation instead of
-- a pasted token. pending_commit remembers a push that arrived while a run was in progress, so the index
-- catches up as soon as that run ends.
ALTER TABLE code_repositories ADD COLUMN installation_id BIGINT;
ALTER TABLE code_repositories ADD COLUMN pending_commit VARCHAR(64);
ALTER TABLE code_repositories DROP COLUMN access_token_ciphertext;

CREATE INDEX idx_code_repositories_installation ON code_repositories (installation_id)
    WHERE installation_id IS NOT NULL;
