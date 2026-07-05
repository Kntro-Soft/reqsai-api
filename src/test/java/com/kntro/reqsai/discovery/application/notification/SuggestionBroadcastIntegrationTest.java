package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionSuggestionMessage;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario #5 (WS broadcast): boots the app, connects a REAL STOMP-over-WebSocket client authenticated with a
 * JWT (exercising {@code StompAuthChannelInterceptor}), subscribes to the session topic
 * ({@code /topic/sessions/{id}}), drives the {@link SuggestionNotificationListener} with a
 * {@link SuggestionCreatedEvent}, and asserts the {@code SUGGESTION_GENERATED} message is broadcast and
 * received over the wire with its payload intact.
 *
 * <p>This lives in the notification package and invokes the (package-private) listener directly — the same
 * deterministic approach the sibling {@code RealtimeNotificationIntegrationTest} uses — so the test skips the
 * async {@code @ApplicationModuleListener}/after-commit machinery while still exercising the full
 * mapper → notifier → broker → client path. The in-memory broker sends no SUBSCRIBE receipt, so
 * {@link #awaitFirst} re-fires the trigger until a frame lands (early broadcasts before the subscription is
 * live are simply lost and retried), keeping the test timing-robust.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Suggestion-generated STOMP broadcast")
class SuggestionBroadcastIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ORG_ID = "00000000-0000-0000-0000-0000000000bb";

    @LocalServerPort
    private int port;

    @Autowired
    private SuggestionNotificationListener suggestionListener;

    private WebSocketStompClient stompClient;

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    @DisplayName("should broadcast a SUGGESTION_GENERATED message to a subscriber of the session topic")
    void should_broadcast_suggestion_generated() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID suggestionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        BlockingQueue<SessionSuggestionMessage> received =
                subscribe(connectAuthenticated(), sessionId, SessionSuggestionMessage.class);

        SuggestionCreatedEvent event = new SuggestionCreatedEvent(
                suggestionId, sessionId, projectId, SuggestionType.NEW_STORY,
                "Autenticación de dos factores", "usuario", "activar 2FA", "proteger mi cuenta",
                Priority.HIGH, 5, null, null, null, List.of(), Instant.now());

        SessionSuggestionMessage msg = awaitFirst(received, () -> suggestionListener.onSuggestionCreated(event));

        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.suggestionId()).isEqualTo(suggestionId);
        assertThat(msg.type()).isEqualTo(SessionEventType.SUGGESTION_GENERATED);
        assertThat(msg.suggestionType()).isEqualTo(SuggestionType.NEW_STORY);
        assertThat(msg.draftTitle()).isEqualTo("Autenticación de dos factores");
        assertThat(msg.draftPriority()).isEqualTo(Priority.HIGH);
    }

    // ----- helpers (mirrors RealtimeNotificationIntegrationTest) -----

    private StompSession connectAuthenticated() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws/stomp",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, UUID sessionId, Class<T> payloadType) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe("/topic/" + SessionTopics.of(sessionId), new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                queue.add(payloadType.cast(payload));
            }
        });
        return queue;
    }

    private <T> T awaitFirst(BlockingQueue<T> queue, Runnable trigger) throws InterruptedException {
        for (int i = 0; i < 25; i++) {
            trigger.run();
            T msg = queue.poll(200, TimeUnit.MILLISECONDS);
            if (msg != null) {
                return msg;
            }
        }
        throw new AssertionError("No realtime message received within 5000ms");
    }
}
