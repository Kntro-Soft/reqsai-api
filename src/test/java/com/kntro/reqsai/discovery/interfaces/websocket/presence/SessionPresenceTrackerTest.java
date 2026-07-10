package com.kntro.reqsai.discovery.interfaces.websocket.presence;

import com.kntro.reqsai.discovery.application.notification.SessionTopics;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionParticipant;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionPresenceMessage;
import com.kntro.reqsai.shared.application.notification.RealtimeNotifier;
import com.kntro.reqsai.shared.infrastructure.web.websocket.StompAuthChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Presence: STOMP tracker")
@ExtendWith(MockitoExtension.class)
class SessionPresenceTrackerTest {

    @Mock
    private SessionParticipantResolver resolver;

    @Mock
    private RealtimeNotifier notifier;

    private SessionPresenceTracker tracker;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tracker = new SessionPresenceTracker(new SessionPresenceRegistry(), resolver, notifier);
    }

    @Test
    @DisplayName("a subscribe to a session topic broadcasts the roster")
    void subscribeBroadcastsRoster() {
        when(resolver.resolve(orgId, alice))
                .thenReturn(new SessionParticipant(alice, "Ana", "/api/users/" + alice + "/avatar"));

        tracker.onSubscribe(subscribe("stomp-1", "sub-1", "/topic/" + SessionTopics.of(sessionId)));

        SessionPresenceMessage message = captureBroadcast();
        assertThat(message.sessionId()).isEqualTo(sessionId);
        assertThat(message.count()).isEqualTo(1);
        assertThat(message.participants()).singleElement()
                .satisfies(p -> assertThat(p.displayName()).isEqualTo("Ana"));
    }

    @Test
    @DisplayName("a subscribe to an unrelated destination is ignored")
    void ignoresUnrelatedDestination() {
        tracker.onSubscribe(subscribe("stomp-1", "sub-1", "/topic/projects/" + UUID.randomUUID() + "/sessions"));

        verify(notifier, never()).broadcast(any(), any());
    }

    @Test
    @DisplayName("a subscribe with no user-id session attribute is ignored, even with a Principal on the frame")
    void ignoresSubscribeMissingUserIdAttribute() {
        // Regression test: the STOMP Principal set on CONNECT does not carry over to later frames in
        // practice (verified against a real Spring STOMP session), so the tracker must not depend on
        // accessor.getUser() — only on the session-attribute identity stashed by the auth interceptor.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("stomp-1");
        accessor.setSubscriptionId("sub-1");
        accessor.setDestination("/topic/" + SessionTopics.of(sessionId));
        Map<String, Object> attributesWithoutUserId = new HashMap<>();
        attributesWithoutUserId.put(StompAuthChannelInterceptor.ORG_ID_ATTRIBUTE, orgId.toString());
        accessor.setSessionAttributes(attributesWithoutUserId);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(alice.toString(), null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        tracker.onSubscribe(new SessionSubscribeEvent(this, message, null));

        verify(notifier, never()).broadcast(any(), any());
    }

    @Test
    @DisplayName("unsubscribing the last subscription rebroadcasts an empty roster")
    void unsubscribeBroadcastsEmptyRoster() {
        when(resolver.resolve(orgId, alice))
                .thenReturn(new SessionParticipant(alice, "Ana", "/api/users/" + alice + "/avatar"));
        tracker.onSubscribe(subscribe("stomp-1", "sub-1", "/topic/" + SessionTopics.of(sessionId)));

        tracker.onUnsubscribe(unsubscribe("stomp-1", "sub-1"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(notifier, org.mockito.Mockito.atLeast(2)).broadcast(eq(SessionTopics.of(sessionId)), payload.capture());
        SessionPresenceMessage last = (SessionPresenceMessage) payload.getValue();
        assertThat(last.count()).isZero();
    }

    @Test
    @DisplayName("disconnect rebroadcasts an empty roster")
    void disconnectBroadcastsEmptyRoster() {
        when(resolver.resolve(orgId, alice))
                .thenReturn(new SessionParticipant(alice, "Ana", "/api/users/" + alice + "/avatar"));
        tracker.onSubscribe(subscribe("stomp-1", "sub-1", "/topic/" + SessionTopics.of(sessionId)));

        tracker.onDisconnect(disconnect("stomp-1"));

        // Last captured broadcast is the empty roster after the disconnect.
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(notifier, org.mockito.Mockito.atLeast(2)).broadcast(eq(SessionTopics.of(sessionId)), payload.capture());
        SessionPresenceMessage last = (SessionPresenceMessage) payload.getValue();
        assertThat(last.count()).isZero();
        assertThat(last.participants()).isEmpty();
    }

    private SessionPresenceMessage captureBroadcast() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(notifier).broadcast(eq(SessionTopics.of(sessionId)), payload.capture());
        return (SessionPresenceMessage) payload.getValue();
    }

    private SessionSubscribeEvent subscribe(String stompSessionId, String subscriptionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(stompSessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(this, message, null);
    }

    private SessionUnsubscribeEvent unsubscribe(String stompSessionId, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(stompSessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setSessionAttributes(sessionAttributes());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionUnsubscribeEvent(this, message, null);
    }

    private SessionDisconnectEvent disconnect(String stompSessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(stompSessionId);
        accessor.setSessionAttributes(sessionAttributes());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, stompSessionId, null);
    }

    private Map<String, Object> sessionAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(StompAuthChannelInterceptor.USER_ID_ATTRIBUTE, alice.toString());
        attributes.put(StompAuthChannelInterceptor.ORG_ID_ATTRIBUTE, orgId.toString());
        return attributes;
    }
}
