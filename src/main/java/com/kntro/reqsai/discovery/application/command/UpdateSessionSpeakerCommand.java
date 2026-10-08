package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The analyst names one diarized speaker of a session and says which side they are on.
 *
 * @param projectId    project the session belongs to
 * @param sessionId    the session
 * @param speakerLabel provider diarization label of the speaker (e.g. {@code "0"})
 * @param displayName  real name, or {@code null}/blank for the default "Hablante N"
 * @param side         client or team, or {@code null} to leave it unset
 */
public record UpdateSessionSpeakerCommand(UUID projectId, UUID sessionId, String speakerLabel,
                                          @Nullable String displayName, @Nullable SpeakerSide side) {
}
