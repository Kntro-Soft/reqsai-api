package com.kntro.reqsai.discovery.suggestionquality;

import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A test streaming-STT provider that lets a WebSocket E2E test control the transcript exactly, with no
 * real Deepgram/AssemblyAI/WhisperLive vendor and no real audio.
 *
 * <p>It replaces the whole {@link StreamingTranscriptionPort} bean (the production
 * {@code StreamingSttRouter} is built with {@code @ConditionalOnMissingBean}, so this {@code @Primary}
 * bean wins). Each session, on every audio frame received via {@link StreamingTranscriptionPort.Session#sendAudio},
 * decodes the frame bytes as <b>UTF-8 text</b> and emits it straight back to the listener as a FINAL
 * {@link StreamingTranscriptionPort.TranscriptEvent} — i.e. the WS client sends the desired transcript
 * sentence AS the binary frame bytes, and this fake "STT" echoes it as a transcript event.
 *
 * <p>This is the exact contract the real handler relies on: {@code SttStreamingWebSocketHandler.handleBinaryMessage}
 * copies the frame's remaining bytes into a {@code byte[]} and calls {@code recognizer.sendAudio(frame)},
 * so decoding those same bytes as UTF-8 reproduces whatever the client wrote. Emitting {@code isFinal=true}
 * makes {@code onTranscript → AppendTranscriptSegmentCommandHandler} persist a segment (which then drives
 * {@code TranscriptSegmentAppendedEvent → RealtimeSuggestionListener → RealtimeSuggestionService}).
 *
 * <p>{@code startMs}/{@code endMs} are synthesized from a per-session monotonic counter so successive
 * segments have increasing, non-overlapping, valid offsets. Empty/blank frames are ignored (a real
 * provider emits nothing for silence), so a client can send keep-alive frames harmlessly.
 */
@TestConfiguration
public class EchoStreamingSttConfig {

    @Bean
    @Primary
    public StreamingTranscriptionPort echoStreamingTranscriptionPort() {
        return (context, listener) -> new StreamingTranscriptionPort.Session() {
            private final AtomicInteger clockMs = new AtomicInteger(0);

            @Override
            public void sendAudio(byte[] frame) {
                String text = new String(frame, StandardCharsets.UTF_8).strip();
                if (text.isEmpty()) {
                    return; // silence / keep-alive — a real provider emits nothing
                }
                int start = clockMs.getAndAdd(1000);
                int end = start + 1000;
                listener.onTranscript(new StreamingTranscriptionPort.TranscriptEvent(
                        text, "0", start, end, /* isFinal */ true));
            }

            @Override
            public void close() {
                // Nothing upstream to release — the fake provider holds no external connection.
            }
        };
    }
}
