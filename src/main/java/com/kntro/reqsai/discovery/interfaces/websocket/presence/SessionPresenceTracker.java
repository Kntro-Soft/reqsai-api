package com.kntro.reqsai.discovery.interfaces.websocket.presence;

import com.kntro.reqsai.discovery.application.notification.SessionTopics;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionParticipant;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionPresenceMessage;
import com.kntro.reqsai.shared.application.avatar.AvatarPaths;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import com.kntro.reqsai.shared.infrastructure.web.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live-presence tracker for discovery sessions. Listens to STOMP lifecycle events and keeps
 * {@link SessionPresenceRegistry} in sync: a SUBSCRIBE to {@code /topic/sessions/{id}} marks the user
 * present, an UNSUBSCRIBE or DISCONNECT removes them. On every real roster change it rebroadcasts the
 * full {@link SessionPresenceMessage} snapshot on that session's topic, so all viewers converge.
 * <p>
 * Presence rides the same per-session topic the client already subscribes to — no extra subscription,
 * and the subscription itself <em>is</em> the presence signal (viewing the live session = present).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionPresenceTracker {

    private static final String SESSION_DESTINATION_PREFIX = "/topic/" + SessionTopics.sessionsPrefix();

    private final SessionPresenceRegistry registry;
    private final SessionParticipantResolver resolver;
    private final RealtimeNotifier notifier;

    @EventListener
    void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID sessionId = parseSessionId(accessor.getDestination());
        Map<String, Object> attributes = accessor.getSessionAttributes();
        UUID userId = attributeUuid(attributes, StompAuthChannelInterceptor.USER_ID_ATTRIBUTE);
        UUID orgId = attributeUuid(attributes, StompAuthChannelInterceptor.ORG_ID_ATTRIBUTE);
        String stompSessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (sessionId == null || userId == null || orgId == null
                || stompSessionId == null || subscriptionId == null) {
            return;
        }
        if (registry.join(sessionId, stompSessionId, subscriptionId, userId)) {
            broadcast(sessionId, orgId);
        }
    }

    @EventListener
    void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String stompSessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (stompSessionId == null || subscriptionId == null) {
            return;
        }
        UUID orgId = attributeUuid(accessor.getSessionAttributes(), StompAuthChannelInterceptor.ORG_ID_ATTRIBUTE);
        registry.leaveSubscription(stompSessionId, subscriptionId)
                .ifPresent(sessionId -> broadcast(sessionId, orgId));
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String stompSessionId = event.getSessionId();
        if (stompSessionId == null) {
            return;
        }
        UUID orgId = attributeUuid(accessor.getSessionAttributes(), StompAuthChannelInterceptor.ORG_ID_ATTRIBUTE);
        for (UUID sessionId : registry.disconnect(stompSessionId)) {
            broadcast(sessionId, orgId);
        }
    }

    /**
     * Rebroadcasts the current roster for a session. {@code orgId} comes from the connection that
     * triggered the change; since a discovery session is single-tenant, every participant resolves
     * under the same organization. A missing {@code orgId} still broadcasts an anonymous roster so
     * the count stays correct.
     */
    private void broadcast(UUID sessionId, UUID orgId) {
        List<SessionParticipant> participants = registry.roster(sessionId).stream()
                .map(userId -> resolveParticipant(orgId, userId))
                .toList();
        notifier.broadcast(SessionTopics.of(sessionId),
                SessionPresenceMessage.of(sessionId, participants, Instant.now()));
        log.debug("Presence for session {}: {} participant(s)", sessionId, participants.size());
    }

    private SessionParticipant resolveParticipant(UUID orgId, UUID userId) {
        if (orgId == null) {
            return new SessionParticipant(userId, SessionParticipantResolver.UNKNOWN_DISPLAY_NAME,
                    AvatarPaths.user(userId));
        }
        return resolver.resolve(orgId, userId);
    }

    private static UUID parseSessionId(String destination) {
        if (destination == null || !destination.startsWith(SESSION_DESTINATION_PREFIX)) {
            return null;
        }
        String raw = destination.substring(SESSION_DESTINATION_PREFIX.length());
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Reads a UUID-valued STOMP session attribute (see {@link StompAuthChannelInterceptor}).
     * Session attributes — unlike the frame's {@code Principal} — persist across every frame of a
     * STOMP session, which is why identity is read from here rather than {@code accessor.getUser()}.
     */
    private static UUID attributeUuid(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        if (!(value instanceof String raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
