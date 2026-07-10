package com.kntro.reqsai.discovery.interfaces.websocket.presence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the presence bookkeeping: join/leave transitions, multi-tab dedup and disconnect. */
class SessionPresenceRegistryTest {

    private final SessionPresenceRegistry registry = new SessionPresenceRegistry();

    private final UUID session = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    @DisplayName("first subscribe makes the user present and reports a roster change")
    void firstSubscribeAddsUser() {
        boolean changed = registry.join(session, "stomp-1", "sub-1", alice);

        assertThat(changed).isTrue();
        assertThat(registry.roster(session)).containsExactly(alice);
    }

    @Test
    @DisplayName("distinct users accumulate in the roster")
    void distinctUsersAccumulate() {
        registry.join(session, "stomp-1", "sub-1", alice);
        boolean changed = registry.join(session, "stomp-2", "sub-1", bob);

        assertThat(changed).isTrue();
        assertThat(registry.roster(session)).containsExactly(alice, bob);
    }

    @Test
    @DisplayName("same user on a second tab does not change the visible roster")
    void secondTabIsDeduped() {
        registry.join(session, "stomp-1", "sub-1", alice);
        boolean changed = registry.join(session, "stomp-2", "sub-1", alice);

        assertThat(changed).isFalse();
        assertThat(registry.roster(session)).containsExactly(alice);
    }

    @Test
    @DisplayName("unsubscribing the last subscription removes the user and reports the change")
    void unsubscribeRemovesUser() {
        registry.join(session, "stomp-1", "sub-1", alice);

        var affected = registry.leaveSubscription("stomp-1", "sub-1");

        assertThat(affected).contains(session);
        assertThat(registry.roster(session)).isEmpty();
    }

    @Test
    @DisplayName("a user stays present until their last tab leaves")
    void userStaysUntilLastTabLeaves() {
        registry.join(session, "stomp-1", "sub-1", alice);
        registry.join(session, "stomp-2", "sub-1", alice);

        var firstLeave = registry.leaveSubscription("stomp-1", "sub-1");
        assertThat(firstLeave).isEmpty();
        assertThat(registry.roster(session)).containsExactly(alice);

        var lastLeave = registry.leaveSubscription("stomp-2", "sub-1");
        assertThat(lastLeave).contains(session);
        assertThat(registry.roster(session)).isEmpty();
    }

    @Test
    @DisplayName("disconnect drops the connection from every session it viewed")
    void disconnectDropsFromAllSessions() {
        UUID otherSession = UUID.randomUUID();
        registry.join(session, "stomp-1", "sub-1", alice);
        registry.join(otherSession, "stomp-1", "sub-2", alice);
        registry.join(session, "stomp-2", "sub-1", bob);

        var affected = registry.disconnect("stomp-1");

        assertThat(affected).containsExactlyInAnyOrder(session, otherSession);
        assertThat(registry.roster(session)).containsExactly(bob);
        assertThat(registry.roster(otherSession)).isEmpty();
    }

    @Test
    @DisplayName("unknown subscription/connection is a no-op")
    void unknownIsNoOp() {
        assertThat(registry.leaveSubscription("ghost", "sub-1")).isEmpty();
        assertThat(registry.disconnect("ghost")).isEmpty();
    }
}
