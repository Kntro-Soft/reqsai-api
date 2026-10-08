package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.SpeakerOverlapReport;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SessionSpeakerResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SessionSpeakersResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SpeakerOverlapsResponse;

import java.util.UUID;

/** Maps a session's speakers and overlapping-speech report to their REST responses. */
public final class SessionSpeakerResponseMapper {

    private SessionSpeakerResponseMapper() {
    }

    public static SessionSpeakersResponse toResponse(UUID sessionId, SpeakerRoster roster, SpeakerOverlapReport overlaps) {
        return new SessionSpeakersResponse(
                sessionId,
                roster.speakers().stream().map(SessionSpeakerResponseMapper::toResponse).toList(),
                toResponse(overlaps));
    }

    public static SessionSpeakerResponse toResponse(SpeakerRoster.Speaker speaker) {
        return new SessionSpeakerResponse(
                speaker.label(),
                speaker.index(),
                speaker.displayName(),
                speaker.name(),
                speaker.side() != null ? speaker.side().name() : null,
                speaker.segmentCount());
    }

    private static SpeakerOverlapsResponse toResponse(SpeakerOverlapReport report) {
        return new SpeakerOverlapsResponse(
                report.count(),
                report.totalMs(),
                report.ranges().stream()
                        .map(r -> new SpeakerOverlapsResponse.Range(r.startMs(), r.endMs(), r.speakerLabels()))
                        .toList());
    }
}
