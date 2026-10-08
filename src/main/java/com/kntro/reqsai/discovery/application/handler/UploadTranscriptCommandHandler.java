package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UploadTranscriptCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Transcribes the uploaded audio via {@link TranscriptionPort} and saves the result in the session,
 * transitioning it from {@code DRAFT} to {@code STOPPED}. The session's meeting language is passed as a
 * hint ({@code es-PE} → {@code es}), so the provider does not have to guess it from the audio.
 *
 * <p>When the provider labelled the speakers (diarization), each utterance is also kept as a final
 * {@link TranscriptSegment} carrying its speaker and timing, numbered from 1 in time order. The session
 * then shows "Hablante 1", "Hablante 2"… like a live one, the analyst can name them, and processing reads
 * the transcript as tagged speaker turns (US40). Without speaker labels only the text is stored, as before.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadTranscriptCommandHandler {

    private static final int SPEAKER_LABEL_MAX = 64;

    private final DiscoverySessionRepository sessions;
    private final TranscriptionPort transcription;
    private final TranscriptSegmentRepository segments;

    @Transactional
    public DiscoverySession handle(UploadTranscriptCommand command) {
        DiscoverySession session = sessions.findById(command.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));

        var result = transcription.transcribe(
                command.audioBytes(), command.filename(), session.getLanguage().primaryLanguage());

        List<TranscriptSegment> diarized = diarizedSegments(session, result);
        session.uploadTranscript(result.text(), result.durationMs(), diarized.size());
        DiscoverySession saved = sessions.save(session);
        if (!diarized.isEmpty()) {
            segments.saveAll(diarized);
        }
        log.info("Transcript uploaded for session {} ({} chars, {}ms, {} speaker segments) — status STOPPED",
                saved.getId(), result.text().length(), result.durationMs(), diarized.size());
        return saved;
    }

    /** The provider's speaker-labelled utterances as final segments in time order; none without diarization. */
    private static List<TranscriptSegment> diarizedSegments(DiscoverySession session, TranscriptionResult result) {
        if (!result.hasDiarization()) {
            return List.of();
        }
        List<TranscriptionResult.SpeakerSegment> utterances = result.segments().stream()
                .filter(u -> u.text() != null && !u.text().isBlank())
                .sorted((a, b) -> Long.compare(a.startMs(), b.startMs()))
                .toList();
        List<TranscriptSegment> out = new ArrayList<>(utterances.size());
        for (TranscriptionResult.SpeakerSegment u : utterances) {
            long start = Math.max(0, u.startMs());
            long end = Math.max(start, u.endMs());
            out.add(new TranscriptSegment(session.getId(), out.size() + 1, label(u.speaker()), u.text().strip(),
                    start, end, true));
        }
        return out;
    }

    private static String label(String speaker) {
        if (speaker == null || speaker.isBlank()) {
            return null;
        }
        String clean = speaker.strip();
        return clean.length() > SPEAKER_LABEL_MAX ? clean.substring(0, SPEAKER_LABEL_MAX) : clean;
    }
}
