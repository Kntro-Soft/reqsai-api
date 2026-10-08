package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes a diarized transcript for the AI, one line per speaker turn, each tagged with who spoke and their
 * side: {@code [Ana (Cliente)]: …}, {@code [Hablante 2 (Equipo)]: …}, {@code [Hablante 3]: …}. Consecutive
 * segments of the same speaker share one line; a segment without a label is written untagged. The
 * generation prompts tell the model to treat the client's words as the requirements.
 *
 * <p>Names are analyst input and the text is speech: square brackets in either become parentheses and line
 * breaks become spaces, so neither can forge a speaker tag. The prompt adapters still neutralize the
 * transcript delimiters on the whole text.
 */
public final class SpeakerTranscriptFormatter {

    /** Side word written in the tag for {@link SpeakerSide#CLIENT}. */
    public static final String CLIENT_TAG = "Cliente";
    /** Side word written in the tag for {@link SpeakerSide#TEAM}. */
    public static final String TEAM_TAG = "Equipo";

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\u2028\\u2029]+");

    private SpeakerTranscriptFormatter() {
    }

    /** True when at least one segment carries a diarization label, so tagging adds information. */
    public static boolean hasSpeakers(List<TranscriptSegment> segments) {
        return segments.stream().anyMatch(s -> s.getSpeakerLabel() != null && !s.getSpeakerLabel().isBlank());
    }

    /** The segments as tagged speaker turns, one per line, in the given order. */
    public static String format(List<TranscriptSegment> segments, SpeakerRoster roster) {
        StringBuilder out = new StringBuilder();
        String currentLabel = null;
        boolean lineOpen = false;
        for (TranscriptSegment segment : segments) {
            String text = clean(segment.getText());
            if (text.isEmpty()) {
                continue;
            }
            String label = segment.getSpeakerLabel() == null || segment.getSpeakerLabel().isBlank()
                    ? null : segment.getSpeakerLabel();
            if (lineOpen && label != null && label.equals(currentLabel)) {
                out.append(' ').append(text);
                continue;
            }
            if (lineOpen) {
                out.append('\n');
            }
            if (label != null) {
                out.append(tag(roster.resolve(label))).append(": ");
            }
            out.append(text);
            currentLabel = label;
            lineOpen = true;
        }
        return out.toString();
    }

    /** {@code [Name (Side)]}, or {@code [Name]} when the side is not set. */
    static String tag(SpeakerRoster.Speaker speaker) {
        String side = sideTag(speaker.side());
        return "[" + clean(speaker.name()) + (side != null ? " (" + side + ")" : "") + "]";
    }

    private static @Nullable String sideTag(@Nullable SpeakerSide side) {
        if (side == null) {
            return null;
        }
        return switch (side) {
            case CLIENT -> CLIENT_TAG;
            case TEAM -> TEAM_TAG;
        };
    }

    private static String clean(String value) {
        return LINE_BREAKS.matcher(value.replace('[', '(').replace(']', ')')).replaceAll(" ").strip();
    }
}
