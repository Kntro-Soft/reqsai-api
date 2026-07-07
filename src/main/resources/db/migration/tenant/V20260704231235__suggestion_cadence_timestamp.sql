-- Realtime suggestion cadence timestamp.
--
-- Records the wall-clock instant of the last realtime suggestion pass that actually ran generation.
-- The realtime service now triggers a pass when EITHER enough new characters have accrued past the
-- watermark OR enough seconds have elapsed since the last pass with new final transcript — whichever
-- comes first. Short back-and-forth exchanges therefore stream out incrementally instead of piling
-- up until the character threshold is crossed and arriving as one late batch.
--
-- Nullable: a session that has never generated has no prior pass instant.
alter table discovery_sessions
    add column if not exists last_suggested_at timestamptz;
