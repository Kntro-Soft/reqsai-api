package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort.Context;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort.Listener;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort.Session;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort.TranscriptEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Keepalive, reconnect and failure handling of the live STT provider streams
 * ({@link AbstractWebSocketStreamingAdapter}), driven through the real Deepgram / AssemblyAI / WhisperLive
 * adapters over a {@link FakeProviderConnector} and a {@link ManualStreamScheduler}: no network, no
 * sleeps, every timing step explicit.
 */
@DisplayName("Infra: live STT provider stream resilience")
class LiveStreamResilienceTest {

    private static final Duration KEEP_ALIVE = Duration.ofSeconds(4);
    private static final Duration BACKOFF = Duration.ofMillis(500);
    private static final int ATTEMPTS = 3;
    /** 100 ms of 16 kHz 16-bit mono audio. */
    private static final int FRAME_BYTES = 3_200;
    private static final int LARGE_BUFFER = 100 * FRAME_BYTES;
    private static final String DEEPGRAM_KEEP_ALIVE = "{\"type\":\"KeepAlive\"}";

    private final ManualStreamScheduler scheduler = new ManualStreamScheduler();
    private final FakeProviderConnector connector = new FakeProviderConnector();
    private final RecordingListener listener = new RecordingListener();
    private final Context context = new Context(UUID.randomUUID(), "es");

    @Nested
    @DisplayName("Keepalive")
    class Keepalive {

        @Test
        @DisplayName("should send Deepgram's KeepAlive text frame on every interval while the stream is idle")
        void should_send_keepalive_while_idle() {
            // Arrange
            openDeepgram();

            // Act
            scheduler.advance(KEEP_ALIVE);
            scheduler.advance(KEEP_ALIVE);

            // Assert
            assertThat(scheduler.activePeriodicTasks()).isEqualTo(1);
            assertThat(connector.latest().textFrames()).containsExactly(DEEPGRAM_KEEP_ALIVE, DEEPGRAM_KEEP_ALIVE);
        }

        @Test
        @DisplayName("should skip the keepalive while an audio frame is still being sent")
        void should_skip_keepalive_while_sending() {
            // Arrange
            Session session = openDeepgram();
            connector.latest().holdSends();
            session.sendAudio(frame(1));

            // Act
            scheduler.advance(KEEP_ALIVE);

            // Assert
            assertThat(connector.latest().textFrames()).isEmpty();
        }

        @Test
        @DisplayName("should cancel the keepalive and close the provider socket when the session closes")
        void should_cancel_keepalive_on_close() {
            // Arrange
            Session session = openDeepgram();

            // Act
            session.close();
            scheduler.advance(Duration.ofSeconds(30));

            // Assert
            assertThat(scheduler.activeTasks()).isZero();
            assertThat(connector.latest().textFrames()).isEmpty();
            assertThat(connector.latest().closeCode()).isEqualTo(1000);
            assertThat(connector.closed()).isTrue();
            assertThat(connector.connectAttempts()).as("the provider's close reply is expected, not a drop").isEqualTo(1);
        }

        @Test
        @DisplayName("should not schedule a keepalive for AssemblyAI, which has no inactivity timeout by default")
        void should_not_keep_alive_assemblyai() {
            // Arrange / Act
            new AssemblyAiStreamingAdapter("test-key", resilience(LARGE_BUFFER)).open(context, listener);
            scheduler.advance(Duration.ofSeconds(30));

            // Assert
            assertThat(scheduler.activePeriodicTasks()).isZero();
            assertThat(connector.latest().textFrames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Provider closes or fails mid-session")
    class Reconnect {

        @Test
        @DisplayName("should reconnect after the backoff and keep forwarding audio, including audio sent meanwhile")
        void should_reconnect_and_continue_audio() {
            // Arrange
            Session session = openDeepgram();
            session.sendAudio(frame(1));

            // Act
            connector.latest().providerCloses(1011, "NET-0001");
            session.sendAudio(frame(2));
            int connectionsDuringBackoff = connector.connections();
            scheduler.advance(BACKOFF);
            session.sendAudio(frame(3));

            // Assert
            assertThat(connectionsDuringBackoff).isEqualTo(1);
            assertThat(connector.connections()).isEqualTo(2);
            assertThat(connector.socket(0).aborted()).isTrue();
            assertThat(connector.socket(0).binaryFrames()).containsExactly(frame(1));
            assertThat(connector.socket(1).binaryFrames()).containsExactly(frame(2), frame(3));
            assertThat(listener.lost).isEmpty();
        }

        @Test
        @DisplayName("should move the keepalive to the new connection")
        void should_keep_alive_new_connection() {
            // Arrange
            openDeepgram();
            connector.latest().providerCloses(1011, "NET-0001");
            scheduler.advance(BACKOFF);

            // Act
            scheduler.advance(KEEP_ALIVE);

            // Assert
            assertThat(scheduler.activePeriodicTasks()).isEqualTo(1);
            assertThat(connector.socket(0).textFrames()).isEmpty();
            assertThat(connector.socket(1).textFrames()).containsExactly(DEEPGRAM_KEEP_ALIVE);
        }

        @Test
        @DisplayName("should reconnect after a provider socket error")
        void should_reconnect_after_error() {
            // Arrange
            Session session = openDeepgram();

            // Act
            connector.latest().providerFails(new IOException("connection reset"));
            scheduler.advance(BACKOFF);
            session.sendAudio(frame(1));

            // Assert
            assertThat(connector.connections()).isEqualTo(2);
            assertThat(connector.socket(1).binaryFrames()).containsExactly(frame(1));
        }

        @Test
        @DisplayName("should continue the transcript timeline: the new connection's offsets follow the earlier audio")
        void should_shift_offsets_after_reconnect() {
            // Arrange
            Session session = openDeepgram();
            session.sendAudio(frame(1));
            session.sendAudio(frame(2));
            session.sendAudio(frame(3));
            connector.latest().providerSends(deepgramFinal("uno", 0.0, 0.3));
            connector.latest().providerCloses(1011, "NET-0001");
            scheduler.advance(BACKOFF);

            // Act
            session.sendAudio(frame(4));
            connector.latest().providerSends(deepgramFinal("dos", 0.5, 1.0));

            // Assert — 300 ms of audio went to the first connection
            assertThat(listener.events).extracting(TranscriptEvent::text).containsExactly("uno", "dos");
            assertThat(listener.events.get(0).startMs()).isZero();
            assertThat(listener.events.get(1).startMs()).isEqualTo(800);
            assertThat(listener.events.get(1).endMs()).isEqualTo(1_800);
        }

        @Test
        @DisplayName("should keep only the newest audio beyond the buffer bound, and account the dropped audio in offsets")
        void should_bound_buffer_while_reconnecting() {
            // Arrange
            Session session = openDeepgram(2 * FRAME_BYTES);
            connector.latest().providerCloses(1011, "NET-0001");

            // Act
            session.sendAudio(frame(1));
            session.sendAudio(frame(2));
            session.sendAudio(frame(3));
            session.sendAudio(frame(4));
            scheduler.advance(BACKOFF);
            connector.latest().providerSends(deepgramFinal("tres", 0.0, 0.1));

            // Assert
            assertThat(connector.socket(1).binaryFrames()).containsExactly(frame(3), frame(4));
            assertThat(listener.events.getFirst().startMs()).isEqualTo(200);
        }

        @Test
        @DisplayName("should send the WhisperLive config again, before any audio, on the new connection")
        void should_resend_handshake_on_reconnect() {
            // Arrange
            Session session = new WhisperLiveStreamingAdapter("ws://localhost:9090", "", "small",
                    resilience(LARGE_BUFFER)).open(context, listener);
            connector.latest().providerCloses(1011, "server restarted");

            // Act
            session.sendAudio(frame(1));
            scheduler.advance(BACKOFF);

            // Assert
            assertThat(connector.socket(0).frames().getFirst()).asString().contains(context.sessionId().toString());
            List<Object> resent = connector.socket(1).frames();
            assertThat(resent).hasSize(2);
            assertThat(resent.getFirst()).asString().contains(context.sessionId().toString());
            assertThat(resent.get(1)).isInstanceOf(byte[].class);
        }
    }

    @Nested
    @DisplayName("Reconnect budget")
    class RetriesExhausted {

        @Test
        @DisplayName("should report the stream lost, once, after every reconnect attempt fails")
        void should_report_lost_after_retries() {
            // Arrange
            Session session = openDeepgram();
            connector.failNextConnects(ATTEMPTS);

            // Act
            connector.latest().providerCloses(1011, "NET-0001");
            scheduler.advance(BACKOFF);
            scheduler.advance(BACKOFF.multipliedBy(2));
            List<String> lostBeforeLastAttempt = List.copyOf(listener.lost);
            scheduler.advance(BACKOFF.multipliedBy(4));

            // Assert
            assertThat(lostBeforeLastAttempt).isEmpty();
            assertThat(listener.lost).hasSize(1);
            assertThat(connector.connectAttempts()).isEqualTo(1 + ATTEMPTS);
            assertThat(connector.closed()).isTrue();
            assertThat(scheduler.activeTasks()).isZero();
            assertThatNoException().isThrownBy(() -> {
                session.sendAudio(frame(1));
                session.close();
            });
            assertThat(connector.connectAttempts()).isEqualTo(1 + ATTEMPTS);
        }

        @Test
        @DisplayName("should report a lost WhisperLive stream through its deduplicating listener")
        void should_report_lost_through_whisperlive_wrapper() {
            // Arrange
            new WhisperLiveStreamingAdapter("ws://localhost:9090", "", "small", resilience(LARGE_BUFFER))
                    .open(context, listener);
            connector.failNextConnects(ATTEMPTS);

            // Act
            connector.latest().providerFails(new IOException("connection reset"));
            scheduler.advance(Duration.ofSeconds(10));

            // Assert
            assertThat(listener.lost).hasSize(1);
        }

        @Test
        @DisplayName("should give up when every new connection dies before delivering a transcript")
        void should_give_up_on_flapping_provider() {
            // Arrange
            openDeepgram();

            // Act
            connector.latest().providerCloses(1011, "NET-0001");
            scheduler.advance(BACKOFF);
            connector.latest().providerCloses(1008, "DATA-0000");
            scheduler.advance(BACKOFF.multipliedBy(2));
            connector.latest().providerCloses(1008, "DATA-0000");
            scheduler.advance(BACKOFF.multipliedBy(4));
            connector.latest().providerCloses(1008, "DATA-0000");

            // Assert
            assertThat(connector.connections()).isEqualTo(1 + ATTEMPTS);
            assertThat(listener.lost).hasSize(1);
        }

        @Test
        @DisplayName("should refill the budget once a reconnected stream delivers a transcript")
        void should_refill_budget_after_healthy_stream() {
            // Arrange
            openDeepgram();
            connector.latest().providerCloses(1011, "NET-0001");
            scheduler.advance(BACKOFF);
            connector.latest().providerSends(deepgramFinal("hola", 0.0, 0.5));

            // Act
            connector.latest().providerCloses(1011, "NET-0001");
            scheduler.advance(BACKOFF);

            // Assert — the first-attempt backoff applies again
            assertThat(connector.connections()).isEqualTo(3);
            assertThat(listener.lost).isEmpty();
        }
    }

    @Nested
    @DisplayName("Sending")
    class Sending {

        @Test
        @DisplayName("should treat a failed send as a broken stream: reconnect and resend that frame")
        void should_reconnect_on_failed_send() {
            // Arrange
            Session session = openDeepgram();
            connector.latest().failSends();

            // Act
            session.sendAudio(frame(7));
            scheduler.advance(BACKOFF);

            // Assert
            assertThat(connector.connections()).isEqualTo(2);
            assertThat(connector.socket(0).binaryFrames()).isEmpty();
            assertThat(connector.socket(1).binaryFrames()).containsExactly(frame(7));
            assertThat(listener.lost).isEmpty();
        }

        @Test
        @DisplayName("should send one frame at a time, the next only once the previous completed")
        void should_serialize_sends() {
            // Arrange
            Session session = openDeepgram();
            FakeProviderSocket socket = connector.latest();
            socket.holdSends();

            // Act
            session.sendAudio(frame(1));
            session.sendAudio(frame(2));
            int sentWhilePending = socket.binaryFrames().size();
            socket.completeHeldSend();

            // Assert
            assertThat(sentWhilePending).isEqualTo(1);
            assertThat(socket.binaryFrames()).containsExactly(frame(1), frame(2));
            assertThat(connector.connections()).as("no 'Send pending' failure, so no reconnect").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class Cleanup {

        @Test
        @DisplayName("should cancel a pending reconnect when the session closes")
        void should_cancel_reconnect_on_close() {
            // Arrange
            Session session = openDeepgram();
            connector.latest().providerCloses(1011, "NET-0001");

            // Act
            session.close();
            scheduler.advance(Duration.ofSeconds(30));

            // Assert
            assertThat(connector.connectAttempts()).isEqualTo(1);
            assertThat(scheduler.activeTasks()).isZero();
            assertThat(connector.closed()).isTrue();
            assertThat(listener.lost).isEmpty();
        }

        @Test
        @DisplayName("should not reconnect when a provider close arrives after the session closed")
        void should_ignore_close_after_session_closed() {
            // Arrange
            Session session = openDeepgram();
            FakeProviderSocket socket = connector.latest();
            session.close();

            // Act
            socket.providerCloses(1011, "NET-0001");
            scheduler.advance(Duration.ofSeconds(30));

            // Assert
            assertThat(connector.connectAttempts()).isEqualTo(1);
            assertThat(listener.lost).isEmpty();
        }
    }

    // ----- helpers -----

    private StreamResilience resilience(int maxBufferedAudioBytes) {
        return new StreamResilience(KEEP_ALIVE, ATTEMPTS, BACKOFF, maxBufferedAudioBytes, Duration.ofSeconds(10),
                scheduler, connector.factory());
    }

    private Session openDeepgram() {
        return openDeepgram(LARGE_BUFFER);
    }

    private Session openDeepgram(int maxBufferedAudioBytes) {
        return new DeepgramStreamingAdapter("test-key", "nova-2", resilience(maxBufferedAudioBytes))
                .open(context, listener);
    }

    private static byte[] frame(int marker) {
        byte[] frame = new byte[FRAME_BYTES];
        Arrays.fill(frame, (byte) marker);
        return frame;
    }

    private static String deepgramFinal(String text, double startSeconds, double durationSeconds) {
        return """
                {"type":"Results","is_final":true,"start":%s,"duration":%s,\
                "channel":{"alternatives":[{"transcript":"%s","words":[]}]}}"""
                .formatted(startSeconds, durationSeconds, text);
    }

    /** Records transcripts and stream-lost reports. */
    private static final class RecordingListener implements Listener {
        private final List<TranscriptEvent> events = new ArrayList<>();
        private final List<String> lost = new ArrayList<>();

        @Override
        public void onTranscript(TranscriptEvent event) {
            events.add(event);
        }

        @Override
        public void onStreamLost(String reason) {
            lost.add(reason);
        }
    }
}
