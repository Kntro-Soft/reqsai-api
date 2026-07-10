package com.kntro.reqsai.discovery.application.notification;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionProcessingFailedEvent;
import com.kntro.reqsai.discovery.domain.event.DiscoverySessionRecordingStartedEvent;
import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionProcessingFailedMessage;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionRealtimeMessage;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStatusChangedMessage;
import com.kntro.reqsai.discovery.interfaces.notification.messages.SessionStoryGeneratedMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.jspecify.annotations.NonNull;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end realtime test: boots the app, connects a real STOMP-over-WebSocket client authenticated
 * with a JWT (exercising {@code StompAuthChannelInterceptor}), subscribes to the canonical session
 * topic, drives the notification listeners, and asserts the typed messages arrive — once per sealed
 * subtype (status change, processing-failed, story-generated) so JSON (de)serialization of each is
 * verified over the wire.
 *
 * <p>The listener handlers are invoked directly (package-private, shared package) to keep the test
 * deterministic, skipping the {@code @ApplicationModuleListener} async/after-commit machinery while
 * still exercising the full mapping → notifier → broker → client path.
 *
 * <p>The in-memory broker emits no SUBSCRIBE receipt, so rather than sleeping a fixed interval before
 * firing, {@link #awaitFirst} re-fires the trigger until a frame lands — early broadcasts (before the
 * subscription is registered) are simply lost and retried, so the test is timing-robust.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Realtime Notifications (STOMP)")
class RealtimeNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ORG_ID = "00000000-0000-0000-0000-0000000000aa";

    /**
     * Converts a raw wire frame (Map) into the typed message once its wire {@code type} is confirmed.
     * Ignores unknown properties: the wire carries a {@code type} discriminator (and presence frames
     * carry {@code participants}/{@code count}) that are not canonical record components.
     */
    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @LocalServerPort
    private int port;

    @Autowired
    private RecordingNotificationListener recordingListener;
    @Autowired
    private ProcessingNotificationListener processingListener;
    @Autowired
    private StoryNotificationListener storyListener;

    private WebSocketStompClient stompClient;

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    @DisplayName("should deliver a status-change message to a subscriber of the session topic")
    void should_deliver_status_change() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        var received = subscribe(connectAuthenticated(), sessionId, SessionStatusChangedMessage.class,
                SessionEventType.RECORDING_STARTED);

        // Act & Assert
        SessionStatusChangedMessage msg = awaitFirst(received,
                () -> recordingListener.onRecordingStarted(DiscoverySessionRecordingStartedEvent.of(sessionId, UUID.randomUUID(), "Kickoff", "es-PE", java.time.Instant.now())));
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.type()).isEqualTo(SessionEventType.RECORDING_STARTED);
    }

    @Test
    @DisplayName("should deliver a FAILED message carrying the reason")
    void should_deliver_failure_reason() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        var received = subscribe(connectAuthenticated(), sessionId, SessionProcessingFailedMessage.class,
                SessionEventType.FAILED);

        // Act & Assert
        SessionProcessingFailedMessage msg = awaitFirst(received,
                () -> processingListener.onProcessingFailed(DiscoverySessionProcessingFailedEvent.of(sessionId, UUID.randomUUID(), "Quota exceeded")));
        assertThat(msg.type()).isEqualTo(SessionEventType.FAILED);
        assertThat(msg.reason()).isEqualTo("Quota exceeded");
    }

    @Test
    @DisplayName("should deliver a STORY_GENERATED message with the full story payload")
    void should_deliver_story_generated() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        var received = subscribe(connectAuthenticated(), sessionId, SessionStoryGeneratedMessage.class,
                SessionEventType.STORY_GENERATED);

        // Act & Assert
        SessionStoryGeneratedMessage msg = awaitFirst(received,
                () -> storyListener.onStoryGenerated(UserStoryCreatedEvent.of(
                        storyId, sessionId, UUID.randomUUID(),
                        "Login con Google", "usuario", "iniciar sesión con Google", "no recordar contraseñas",
                        Priority.HIGH, 5)));
        assertThat(msg.sessionId()).isEqualTo(sessionId);
        assertThat(msg.storyId()).isEqualTo(storyId);
        assertThat(msg.type()).isEqualTo(SessionEventType.STORY_GENERATED);
        assertThat(msg.title()).isEqualTo("Login con Google");
        assertThat(msg.priority()).isEqualTo(Priority.HIGH);
        assertThat(msg.storyPoints()).isEqualTo(5);
    }

    @Test
    @DisplayName("should reject a CONNECT carrying an invalid token")
    void should_reject_invalid_token() {
        // Act & Assert — the auth interceptor throws on the bad token, so the CONNECT never completes.
        assertThatThrownBy(() -> connect("Bearer not-a-valid-jwt"))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    // ----- helpers -----

    private StompSession connectAuthenticated() throws Exception {
        return connect(TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER"));
    }

    private StompSession connect(String authorization) throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", authorization);

        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws/stomp",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * Subscribes and filters incoming frames to {@code expectedType} before enqueuing. A subscriber
     * to a session topic can now also receive an automatic {@code PRESENCE_STATE} broadcast (see
     * {@code SessionPresenceTracker}) the instant it subscribes — the JSON still decodes cleanly into
     * whatever {@code payloadType} the caller asked for (a record's extra unmapped fields are just
     * ignored), so without this filter the spurious presence frame would race the real one under test.
     */
    private <T extends SessionRealtimeMessage> BlockingQueue<T> subscribe(
            StompSession session, UUID sessionId, Class<T> payloadType, SessionEventType expectedType) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        // Read each frame as a raw Map first: every concrete message hardcodes type() as a fixed
        // constant (not a JSON-mapped record component), so force-casting a frame into payloadType makes
        // type() lie. In particular the automatic PRESENCE_STATE broadcast (sent the instant a client
        // subscribes) would deserialize into e.g. SessionProcessingFailedMessage with type()==FAILED and
        // a null reason, defeating a type()-based filter. Discriminate on the WIRE type instead, then
        // convert only genuine matches into the typed record.
        session.subscribe("/topic/" + SessionTopics.of(sessionId), new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return java.util.Map.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> frame = (java.util.Map<String, Object>) payload;
                if (expectedType.name().equals(String.valueOf(frame.get("type")))) {
                    queue.add(WIRE_MAPPER.convertValue(frame, payloadType));
                }
            }
        });
        return queue;
    }

    /**
     * Fires {@code trigger} until a message lands on {@code queue} (or fails after ~5s). Avoids a fixed
     * pre-subscribe sleep: if the SUBSCRIBE is not yet registered the early broadcasts are lost and we
     * retry; once live, the next broadcast is delivered.
     */
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
