-- US40: the analyst names each diarized speaker of a session and says whether they are the client or the
-- team. The speakers themselves come from transcript_segments.speaker_label; a row only exists once the
-- analyst described one.
create table session_speakers
(
    id            uuid        primary key,
    session_id    uuid        not null,
    speaker_label varchar(64) not null,
    display_name  varchar(80),
    side          varchar(16), -- SpeakerSide enum: CLIENT | TEAM

    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    created_by    uuid,
    updated_by    uuid,

    constraint uq_session_speakers_session_label unique (session_id, speaker_label)
);
