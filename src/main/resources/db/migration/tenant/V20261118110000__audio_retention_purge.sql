-- US69: purge of expired audios. A session keeps the recording it was uploaded with until the
-- organization's audio retention period (public.organizations.audio_retention_days) runs out; the
-- scheduled purge then deletes the audio and stamps when it did, while the transcript, the segments
-- and the stories of the session stay untouched.
ALTER TABLE discovery_sessions
    ADD COLUMN audio_stored_at TIMESTAMPTZ,
    ADD COLUMN audio_purged_at TIMESTAMPTZ;

-- The audio bytes live apart from the session row so that listing sessions never reads them.
CREATE TABLE session_audio_contents (
    id         UUID         NOT NULL PRIMARY KEY,
    session_id UUID         NOT NULL REFERENCES discovery_sessions (id) ON DELETE CASCADE,
    file_name  VARCHAR(255),
    size_bytes BIGINT       NOT NULL,
    content    BYTEA        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by UUID,
    updated_by UUID,

    CONSTRAINT uq_session_audio_contents_session UNIQUE (session_id)
);

-- Candidates of the purge: audio still stored, oldest first.
CREATE INDEX idx_sessions_audio_purge ON discovery_sessions (audio_stored_at)
    WHERE audio_stored_at IS NOT NULL AND audio_purged_at IS NULL;
