package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.SessionSpeakerRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.domain.model.SpeakerOverlapDetector;
import com.kntro.reqsai.discovery.domain.model.SpeakerOverlapReport;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.domain.model.SpeakerSpan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reads who spoke in a session: the diarized speakers numbered by first appearance and merged with the
 * names and sides the analyst gave ({@link SpeakerRoster}), plus where they talked over each other. Shared
 * by the speakers endpoint and by the AI passes that tag the transcript with speaker names.
 */
@Component
@RequiredArgsConstructor
public class SessionSpeakerService {

    private final TranscriptSegmentRepository segments;
    private final SessionSpeakerRepository speakers;

    /** The session's speakers (empty when the transcript carries no diarization). */
    public SpeakerRoster rosterOf(UUID sessionId) {
        return rosterFrom(sessionId, segments.findSpeakerSpans(sessionId));
    }

    /** The session's speakers and its overlapping-speech stretches, from one read of the segment spans. */
    public Overview overviewOf(UUID sessionId) {
        List<SpeakerSpan> spans = segments.findSpeakerSpans(sessionId);
        return new Overview(rosterFrom(sessionId, spans), SpeakerOverlapDetector.detect(spans));
    }

    private SpeakerRoster rosterFrom(UUID sessionId, List<SpeakerSpan> spans) {
        if (spans.isEmpty()) {
            return SpeakerRoster.empty();
        }
        return SpeakerRoster.of(spans, speakers.findAllBySessionId(sessionId));
    }

    /** A session's speakers and where they overlapped. */
    public record Overview(SpeakerRoster roster, SpeakerOverlapReport overlaps) {
    }
}
