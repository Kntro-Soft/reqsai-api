package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.time.Duration;

/**
 * Keepalive and reconnect policy of a live provider stream, plus the scheduler and connector it runs on.
 *
 * @param keepAliveInterval     how often an idle stream sends the provider's keepalive frame
 * @param maxReconnectAttempts  consecutive reconnect attempts before the stream is declared lost
 * @param reconnectBackoff      delay before the first attempt; it doubles on each further attempt
 * @param maxBufferedAudioBytes audio kept while the provider is unreachable; the oldest is dropped beyond it
 * @param connectTimeout        handshake timeout of each connection attempt
 * @param scheduler             runs the keepalive ticks and the reconnect attempts
 * @param connectors            opens the provider sockets of each stream
 */
record StreamResilience(
        Duration keepAliveInterval,
        int maxReconnectAttempts,
        Duration reconnectBackoff,
        int maxBufferedAudioBytes,
        Duration connectTimeout,
        StreamScheduler scheduler,
        ProviderConnector.Factory connectors) {

    /** Within Deepgram's documented 3–5 s keepalive cadence (it closes after 10 s without data). */
    static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(4);
    static final int MAX_RECONNECT_ATTEMPTS = 3;
    /** 0.5 s, 1 s, 2 s. */
    static final Duration RECONNECT_BACKOFF = Duration.ofMillis(500);
    /** 30 s of 16 kHz 16-bit mono audio, enough to bridge a long JVM pause plus the reconnect. */
    static final int MAX_BUFFERED_AUDIO_BYTES = 30_000 * AbstractWebSocketStreamingAdapter.AUDIO_BYTES_PER_MS;
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    static StreamResilience defaults() {
        return new StreamResilience(KEEP_ALIVE_INTERVAL, MAX_RECONNECT_ATTEMPTS, RECONNECT_BACKOFF,
                MAX_BUFFERED_AUDIO_BYTES, CONNECT_TIMEOUT, StreamScheduler.shared(), JdkProviderConnector::new);
    }
}
