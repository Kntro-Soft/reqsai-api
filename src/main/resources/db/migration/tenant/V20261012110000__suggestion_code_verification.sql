-- Deep code verification: the analyst can ask to check a suggestion against the pure source code in GitHub.
-- The verification runs in the background, so the suggestion keeps where it stands, and its verdict.
alter table suggestions
    add column code_verify_state varchar(16),  -- CodeVerifyState: VERIFYING | VERIFIED | FAILED
    add column code_verdict      varchar(24),  -- CodeVerdict: ALREADY_EXISTS | CONFLICTS_WITH_CODE | NOT_FOUND | UNSURE
    add column code_verified_at  timestamptz,
    add column code_verify_error varchar(300);
