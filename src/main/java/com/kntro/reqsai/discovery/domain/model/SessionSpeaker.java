package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.SessionSpeakerUpdatedEvent;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What the analyst said about one diarized speaker of a session: a real name and the side they are on
 * (client or team). The speaker itself comes from the transcript ({@code TranscriptSegment.speakerLabel});
 * this row only exists once the analyst described them, and the pair {@code (sessionId, speakerLabel)} is
 * unique. Applies to every segment of that speaker, past and future.
 */
@Entity
@Table(name = "session_speakers")
@Getter
public class SessionSpeaker extends AggregateRoot {

    /** Longest speaker name. */
    public static final int NAME_MAX = 80;
    private static final int LABEL_MAX = 64;
    private static final int SIDE_MAX = 16;
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Cntrl}]+");

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "speaker_label", nullable = false, updatable = false, length = LABEL_MAX)
    private String speakerLabel;

    @Column(name = "display_name", length = NAME_MAX)
    private @Nullable String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = SIDE_MAX)
    private @Nullable SpeakerSide side;

    protected SessionSpeaker() {
        super();
    }

    public SessionSpeaker(UUID sessionId, String speakerLabel) {
        super();
        this.sessionId = Assert.notNull(sessionId, "sessionId");
        this.speakerLabel = Assert.maxLength(Assert.notBlank(speakerLabel, "speakerLabel"), "speakerLabel", LABEL_MAX);
    }

    /**
     * Names the speaker and sets their side. A blank name goes back to the default "Hablante N"; line
     * breaks, tabs and control characters become single spaces, so a name always fits on one line.
     */
    public void describe(@Nullable String displayName, @Nullable SpeakerSide side) {
        this.displayName = normalizeName(displayName);
        this.side = side;
        registerEvent(SessionSpeakerUpdatedEvent.of(sessionId, speakerLabel, this.displayName, side));
    }

    /** The name as stored: whitespace collapsed and trimmed, {@code null} when blank, at most {@link #NAME_MAX}. */
    static @Nullable String normalizeName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String clean = WHITESPACE.matcher(name).replaceAll(" ").strip();
        return clean.isEmpty() ? null : Assert.maxLength(clean, "displayName", NAME_MAX);
    }
}
