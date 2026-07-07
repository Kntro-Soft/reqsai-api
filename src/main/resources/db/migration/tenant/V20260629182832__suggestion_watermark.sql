-- Realtime suggestion watermark.
--
-- Tracks the highest *final* transcript-segment sequence that has already been turned into
-- suggestions for a session. The realtime suggestion service processes only segments past this
-- watermark and advances it solely on success, so overlapping context windows never re-process
-- (no duplicate suggestions) and a transient LLM/STT failure is retried instead of being lost.
alter table discovery_sessions
    add column if not exists last_suggested_sequence integer not null default 0;
