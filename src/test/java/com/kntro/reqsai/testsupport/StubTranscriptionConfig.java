package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Deterministic stand-in for the Whisper transcription adapter, shared by all integration tests that
 * exercise the upload-transcript path.
 * <p>
 * Always returns the same hardcoded transcript so tests can assert on transcript content and
 * story extraction without a running Whisper server.
 * <p>
 * Usage: {@code @Import(StubTranscriptionConfig.class)} on any {@code @SpringBootTest} that calls
 * {@code POST /sessions/{id}/upload}.
 */
@TestConfiguration
public class StubTranscriptionConfig {

    public static final String STUB_TRANSCRIPT =
            "El cliente necesita un sistema de login con Google. " +
            "También quiere que los usuarios puedan gestionar su perfil. " +
            "Es importante que haya roles de administrador y usuario estándar.";

    @Bean
    @Primary
    public TranscriptionPort stubTranscriptionPort() {
        return (_, _) -> TranscriptionResult.textOnly(STUB_TRANSCRIPT, 60_000L);
    }
}
