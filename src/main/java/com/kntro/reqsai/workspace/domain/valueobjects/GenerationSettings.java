package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Organization-wide generation preferences (US14/US15). Immutable value object, mapped as an
 * {@code @Embeddable} (its fields become real columns on {@code organizations}, so they stay queryable):
 * {@code meetingLanguage} → {@code meeting_language} (via the shared {@code LanguageCodeConverter}),
 * {@code audioRetentionDays} → {@code audio_retention_days}.
 *
 * @param meetingLanguage    default meeting language (BCP-47); used by STT and the LLM, and inherited
 *                           by new discovery sessions
 * @param audioRetentionDays days to keep audio after transcription: {@code 0} = delete immediately,
 *                           {@code -1} = keep forever
 */
@Embeddable
public record GenerationSettings(

        @Column(name = "meeting_language", nullable = false, length = 8)
        LanguageCode meetingLanguage,

        @Column(name = "audio_retention_days", nullable = false)
        int audioRetentionDays
) {

    private static final LanguageCode DEFAULT_LANGUAGE = LanguageCode.of("es-PE");
    private static final int DEFAULT_RETENTION_DAYS = 30;

    public GenerationSettings {
        if (meetingLanguage == null) {
            throw Exceptions.invalidValue("meetingLanguage", "cannot be null");
        }
        if (audioRetentionDays < -1) {
            throw Exceptions.invalidValue("audioRetentionDays", "must be >= -1");
        }
    }

    /** Sensible defaults applied to a brand-new organization. */
    public static GenerationSettings defaults() {
        return new GenerationSettings(DEFAULT_LANGUAGE, DEFAULT_RETENTION_DAYS);
    }

    public static GenerationSettings of(LanguageCode meetingLanguage, int audioRetentionDays) {
        return new GenerationSettings(meetingLanguage, audioRetentionDays);
    }

    /**
     * Returns a copy with only the provided (non-null) fields changed; {@code null} arguments keep the
     * current value. Used by partial (PATCH) updates so a field can be left untouched.
     */
    public GenerationSettings withChanges(LanguageCode meetingLanguage, Integer audioRetentionDays) {
        return new GenerationSettings(
                meetingLanguage != null ? meetingLanguage : this.meetingLanguage,
                audioRetentionDays != null ? audioRetentionDays : this.audioRetentionDays);
    }
}
