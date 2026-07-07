-- Transcript segments — tenant-scoped. Fragments of a session's live transcript produced

CREATE TABLE transcript_segments (
    id            UUID         PRIMARY KEY,
    session_id    UUID         NOT NULL,
    sequence      INTEGER      NOT NULL,
    speaker_label VARCHAR(64),
    text          TEXT         NOT NULL,
    start_ms      BIGINT       NOT NULL,
    end_ms        BIGINT       NOT NULL,
    is_final      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    UUID,
    updated_by    UUID
);

CREATE INDEX idx_segments_session ON transcript_segments (session_id, sequence);
-- Unique index enforces idempotency on reconnect (sequence monotonic per session)
CREATE UNIQUE INDEX uq_segments_session_sequence ON transcript_segments (session_id, sequence);
