package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming;

import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the streaming STT strategy: the configured provider name routes {@code open} to the
 * matching adapter; an unknown/blank provider fails fast (no real-time STT to fall back to).
 *
 * @see StreamingSttRouter
 */
@DisplayName("Infra: StreamingSttRouter")
class StreamingSttRouterTest {

    private final NamedPort deepgram = new NamedPort("deepgram");
    private final NamedPort assemblyAi = new NamedPort("assemblyai");
    private final NamedPort whisperLive = new NamedPort("whisperlive");

    @Test
    @DisplayName("should route to deepgram")
    void should_route_deepgram() {
        assertThat(routedProvider("deepgram")).isEqualTo("deepgram");
    }

    @Test
    @DisplayName("should route to assemblyai")
    void should_route_assemblyai() {
        assertThat(routedProvider("assemblyai")).isEqualTo("assemblyai");
    }

    @Test
    @DisplayName("should route to whisperlive")
    void should_route_whisperlive() {
        assertThat(routedProvider("whisperlive")).isEqualTo("whisperlive");
    }

    @Test
    @DisplayName("should be case-insensitive on the provider name")
    void should_be_case_insensitive() {
        assertThat(routedProvider("DeepGram")).isEqualTo("deepgram");
    }

    @Test
    @DisplayName("should fail fast for an unknown provider")
    void should_reject_unknown() {
        var router = new StreamingSttRouter("foobar", deepgram, assemblyAi, whisperLive);
        assertThatThrownBy(() -> router.open(context(), _ -> { }))
                .isInstanceOf(InfrastructureException.class);
    }

    @Test
    @DisplayName("should fail fast when provider is null")
    void should_reject_null() {
        var router = new StreamingSttRouter(null, deepgram, assemblyAi, whisperLive);
        assertThatThrownBy(() -> router.open(context(), event -> { }))
                .isInstanceOf(InfrastructureException.class);
    }

    private String routedProvider(String provider) {
        var router = new StreamingSttRouter(provider, deepgram, assemblyAi, whisperLive);
        var session = router.open(context(), event -> { });
        return ((NamedSession) session).name();
    }

    private StreamingTranscriptionPort.Context context() {
        return new StreamingTranscriptionPort.Context(UUID.randomUUID(), "es");
    }

    /**
     * Fake adapter that reports which provider was selected via the returned session.
     */
    private record NamedPort(String name) implements StreamingTranscriptionPort {

    @Override
        public Session open(Context context, Listener listener) {
            return new NamedSession(name);
        }
    }

    private record NamedSession(String name) implements StreamingTranscriptionPort.Session {
        @Override
        public void sendAudio(byte[] frame) {
        }

        @Override
        public void close() {
        }
    }
}
