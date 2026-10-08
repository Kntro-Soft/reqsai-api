package com.kntro.reqsai.discovery.infrastructure.ai.transcription.batch.strategy;

import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AssemblyAiAdapter} against a mocked AssemblyAI API: the language hint is sent
 * as {@code language_code} when given, and left out (provider auto-detect) when absent.
 */
@DisplayName("Infrastructure: AssemblyAI batch transcription")
class AssemblyAiAdapterTest {

    private static final String BASE = "https://api.assemblyai.com/v2";

    private MockRestServiceServer server;
    private AssemblyAiAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AssemblyAiAdapter(builder.build(), "test-key");
    }

    private void expectUpload() {
        server.expect(requestTo(BASE + "/upload"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"upload_url\":\"https://cdn.example/a.mp3\"}", MediaType.APPLICATION_JSON));
    }

    private void expectCompletedJob() {
        server.expect(requestTo(BASE + "/transcript/job-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"job-1","status":"completed","text":"Quiero reservar una mesa.",
                         "language_code":"es","audio_duration":12.5}""", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("should send the language hint as language_code")
    void should_send_language_code_when_hinted() {
        expectUpload();
        server.expect(requestTo(BASE + "/transcript"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.audio_url").value("https://cdn.example/a.mp3"))
                .andExpect(jsonPath("$.language_code").value("es"))
                .andRespond(withSuccess("{\"id\":\"job-1\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));
        expectCompletedJob();

        TranscriptionResult result = adapter.transcribe(new byte[]{1, 2, 3}, "reunion.mp3", "es");

        assertThat(result.text()).isEqualTo("Quiero reservar una mesa.");
        assertThat(result.durationMs()).isEqualTo(12_500L);
        server.verify();
    }

    @Test
    @DisplayName("should leave language_code out so AssemblyAI auto-detects when there is no hint")
    void should_omit_language_code_without_hint() {
        expectUpload();
        server.expect(requestTo(BASE + "/transcript"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.language_code").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"job-1\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));
        expectCompletedJob();

        adapter.transcribe(new byte[]{1, 2, 3}, "reunion.mp3", null);

        server.verify();
    }
}
