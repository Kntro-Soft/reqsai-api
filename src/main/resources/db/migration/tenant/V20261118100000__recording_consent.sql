-- US70: the analyst confirms that the participants consented to the recording (Ley N.° 29733)
-- before the live capture starts; who confirmed and when are kept per session.
alter table discovery_sessions add column recording_consent_at timestamptz;
alter table discovery_sessions add column recording_consent_by uuid;
