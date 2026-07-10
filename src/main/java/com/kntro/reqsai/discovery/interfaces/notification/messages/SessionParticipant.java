package com.kntro.reqsai.discovery.interfaces.notification.messages;

import java.util.UUID;

/**
 * One user currently present in a live discovery session, as carried by {@link SessionPresenceMessage}.
 *
 * @param userId     the participant's user id (JWT {@code sub})
 * @param displayName the member display name resolved from the workspace roster; may fall back to a
 *                    generic label when the membership cannot be resolved
 * @param avatarUrl  the public avatar serve path for the user (loadable directly by an {@code <img>})
 */
public record SessionParticipant(UUID userId, String displayName, String avatarUrl) {
}
