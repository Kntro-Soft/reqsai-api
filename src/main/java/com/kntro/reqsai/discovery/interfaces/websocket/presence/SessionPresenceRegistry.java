package com.kntro.reqsai.discovery.interfaces.websocket.presence;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process registry of who is currently viewing each live discovery session, driven by STOMP
 * subscribe/unsubscribe/disconnect events. Deliberately <strong>not</strong> Redis: presence is
 * ephemeral, per-connection state, so it lives in the JVM alongside the in-memory broker.
 *
 * <p><strong>Scaling note:</strong> like the {@code SIMPLE} broker (ADR-0007), this registry only
 * sees connections on the local JVM. With multiple instances behind a {@code RELAY} broker, each
 * instance tracks its own slice; a fully global roster would need the broker's shared state. This is
 * acceptable at single-instance scale and is the same trade-off already accepted for broadcasts.
 *
 * <p>Presence is keyed by discovery {@code sessionId}. A single user viewing from two browser tabs
 * (two STOMP sessions) counts once — {@link #roster(UUID)} returns distinct user ids. All mutating
 * methods return whether the visible roster for a session actually changed, so the caller only
 * re-broadcasts on real transitions.
 */
@Component
public class SessionPresenceRegistry {

    /** discovery sessionId → (stompSessionId → userId) of everyone currently subscribed. */
    private final Map<UUID, Map<String, UUID>> presenceBySession = new ConcurrentHashMap<>();

    /** stompSessionId → (subscriptionId → discovery sessionId), to resolve unsubscribe/disconnect. */
    private final Map<String, Map<String, UUID>> subscriptionsByStomp = new ConcurrentHashMap<>();

    /**
     * Records that {@code userId} subscribed to {@code sessionId} on a STOMP connection.
     *
     * @return {@code true} when this made the user newly present in the session (roster grew)
     */
    public synchronized boolean join(UUID sessionId, String stompSessionId, String subscriptionId, UUID userId) {
        subscriptionsByStomp
                .computeIfAbsent(stompSessionId, k -> new ConcurrentHashMap<>())
                .put(subscriptionId, sessionId);
        boolean userWasPresent = isUserPresent(sessionId, userId);
        presenceBySession
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(stompSessionId, userId);
        return !userWasPresent;
    }

    /**
     * Removes a single subscription (STOMP UNSUBSCRIBE). If it was the connection's last subscription
     * to that session, the connection stops being present.
     *
     * @return the affected session id when the visible roster changed, otherwise empty
     */
    public synchronized Optional<UUID> leaveSubscription(String stompSessionId, String subscriptionId) {
        Map<String, UUID> subs = subscriptionsByStomp.get(stompSessionId);
        if (subs == null) {
            return Optional.empty();
        }
        UUID sessionId = subs.remove(subscriptionId);
        if (subs.isEmpty()) {
            subscriptionsByStomp.remove(stompSessionId);
        }
        if (sessionId == null || subs.containsValue(sessionId)) {
            // Unknown subscription, or the connection still views this session via another subscription.
            return Optional.empty();
        }
        return removeConnectionFromSession(sessionId, stompSessionId);
    }

    /**
     * Removes a whole STOMP connection (DISCONNECT), dropping it from every session it viewed.
     *
     * @return the set of sessions whose visible roster changed
     */
    public synchronized Set<UUID> disconnect(String stompSessionId) {
        Map<String, UUID> subs = subscriptionsByStomp.remove(stompSessionId);
        if (subs == null) {
            return Set.of();
        }
        Set<UUID> changed = new LinkedHashSet<>();
        for (UUID sessionId : Set.copyOf(subs.values())) {
            removeConnectionFromSession(sessionId, stompSessionId).ifPresent(changed::add);
        }
        return changed;
    }

    /** Distinct user ids currently present in {@code sessionId}, insertion-ordered. */
    public synchronized List<UUID> roster(UUID sessionId) {
        Map<String, UUID> present = presenceBySession.get(sessionId);
        if (present == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(present.values()));
    }

    private Optional<UUID> removeConnectionFromSession(UUID sessionId, String stompSessionId) {
        Map<String, UUID> present = presenceBySession.get(sessionId);
        if (present == null) {
            return Optional.empty();
        }
        UUID removedUser = present.remove(stompSessionId);
        if (present.isEmpty()) {
            presenceBySession.remove(sessionId);
        }
        if (removedUser == null) {
            return Optional.empty();
        }
        // Roster only changed if that user is no longer present via another connection (another tab).
        return present.containsValue(removedUser) ? Optional.empty() : Optional.of(sessionId);
    }

    private boolean isUserPresent(UUID sessionId, UUID userId) {
        Map<String, UUID> present = presenceBySession.get(sessionId);
        return present != null && present.containsValue(userId);
    }
}
