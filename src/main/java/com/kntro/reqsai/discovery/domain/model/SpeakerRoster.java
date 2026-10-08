package com.kntro.reqsai.discovery.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The diarized speakers of one session, numbered by first appearance in the transcript ("Hablante 1",
 * "Hablante 2", …) and merged with what the analyst said about each one ({@link SessionSpeaker}: a real name
 * and a side). Immutable; built from the session's {@link SpeakerSpan}s in transcript order.
 */
public final class SpeakerRoster {

    /** Prefix of the default name of a speaker the analyst has not named. */
    public static final String DEFAULT_NAME_PREFIX = "Hablante ";

    private static final SpeakerRoster EMPTY = new SpeakerRoster(List.of());

    private final List<Speaker> speakers;

    private SpeakerRoster(List<Speaker> speakers) {
        this.speakers = Collections.unmodifiableList(speakers);
    }

    /** A roster with no speakers (transcript without diarization). */
    public static SpeakerRoster empty() {
        return EMPTY;
    }

    /**
     * Builds the roster from the spans in transcript order: each distinct label is numbered when it first
     * appears and counts its segments. Stored descriptions of labels that never spoke are ignored.
     */
    public static SpeakerRoster of(List<SpeakerSpan> spansInOrder, Collection<SessionSpeaker> described) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SpeakerSpan span : spansInOrder) {
            if (span.speakerLabel() != null && !span.speakerLabel().isBlank()) {
                counts.merge(span.speakerLabel(), 1, Integer::sum);
            }
        }
        Map<String, SessionSpeaker> byLabel = new LinkedHashMap<>();
        for (SessionSpeaker speaker : described) {
            byLabel.put(speaker.getSpeakerLabel(), speaker);
        }
        List<Speaker> speakers = new ArrayList<>(counts.size());
        int index = 1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            SessionSpeaker stored = byLabel.get(entry.getKey());
            speakers.add(new Speaker(entry.getKey(), index++,
                    stored != null ? stored.getDisplayName() : null,
                    stored != null ? stored.getSide() : null,
                    entry.getValue()));
        }
        return new SpeakerRoster(speakers);
    }

    /** Speakers in order of first appearance. */
    public List<Speaker> speakers() {
        return speakers;
    }

    public boolean isEmpty() {
        return speakers.isEmpty();
    }

    /** The speaker with this diarization label, if they spoke in the session. */
    public Optional<Speaker> find(@Nullable String label) {
        if (label == null) {
            return Optional.empty();
        }
        return speakers.stream().filter(s -> s.label().equals(label)).findFirst();
    }

    /**
     * The speaker with this label, or a new unnamed one numbered after the known speakers when the label
     * is not in the roster yet (a speaker who just started talking).
     */
    public Speaker resolve(String label) {
        return find(label).orElseGet(() -> new Speaker(label, speakers.size() + 1, null, null, 0));
    }

    /** The default name of the speaker that appeared {@code index}-th: "Hablante {index}". */
    public static String defaultName(int index) {
        return DEFAULT_NAME_PREFIX + index;
    }

    /**
     * One speaker of the session.
     *
     * @param label        provider diarization label (e.g. {@code "0"}, {@code "A"})
     * @param index        1-based position by first appearance
     * @param displayName  the name the analyst gave, or {@code null}
     * @param side         client or team, or {@code null} when not set
     * @param segmentCount final segments attributed to this speaker
     */
    public record Speaker(String label, int index, @Nullable String displayName, @Nullable SpeakerSide side,
                          int segmentCount) {

        /** The name to show: the analyst's, else "Hablante N". */
        public String name() {
            return displayName != null ? displayName : defaultName(index);
        }
    }
}
