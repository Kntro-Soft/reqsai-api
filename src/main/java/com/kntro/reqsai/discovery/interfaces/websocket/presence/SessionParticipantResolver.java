package com.kntro.reqsai.discovery.interfaces.websocket.presence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionParticipant;
import com.kntro.reqsai.shared.application.avatar.AvatarPaths;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Turns a bare {@code userId} (plus the connection's tenant) into a display-ready
 * {@link SessionParticipant} for the presence roster.
 * <p>
 * The display name comes from the workspace member roster through the {@code workspace::api} ACL and
 * is <strong>Caffeine-cached</strong> (keyed by tenant + user) so a chatty stream of join/leave
 * events does not hit the database on every transition. The avatar URL is deterministic
 * ({@link AvatarPaths#user(UUID)}) and needs no lookup.
 */
@Component
@RequiredArgsConstructor
public class SessionParticipantResolver {

    /** Shown when a user id cannot be matched to an active membership (e.g. removed mid-session). */
    static final String UNKNOWN_DISPLAY_NAME = "Participant";

    private final WorkspaceModuleApi workspace;

    private final Cache<String, String> displayNames = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * Resolves the participant view for {@code userId} in {@code orgId}. Never returns {@code null}:
     * an unresolved membership falls back to a generic label so a present user is still shown.
     */
    public SessionParticipant resolve(UUID orgId, UUID userId) {
        String displayName = displayNames.get(cacheKey(orgId, userId), key ->
                workspace.findMemberDisplayName(orgId, userId).orElse(UNKNOWN_DISPLAY_NAME));
        return new SessionParticipant(userId, displayName, AvatarPaths.user(userId));
    }

    private static String cacheKey(UUID orgId, UUID userId) {
        return orgId + ":" + userId;
    }
}
