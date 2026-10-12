package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.codebase.api.CodeSnippet;
import com.kntro.reqsai.codebase.api.DeepCodeSearchPort;
import com.kntro.reqsai.codebase.api.DeepSearchResult;
import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort.SubjectKind;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Discovery: deep code verification")
class CodeVerificationServiceTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final CodeSnippet SNIPPET = new CodeSnippet("acme/reservas", "abc", "src/a.ts", 1, 3, "const A = 1;",
            "https://github.com/acme/reservas/blob/abc/src/a.ts#L1-L3", 5.0);

    private final DeepCodeSearchPort search = mock(DeepCodeSearchPort.class);
    private final CodeVerificationPort verifier = mock(CodeVerificationPort.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final List<Long> tokens = new java.util.concurrent.CopyOnWriteArrayList<>();
    private ExecutorService pool;
    private CodeVerificationService service;

    @BeforeEach
    void setUp() {
        pool = Executors.newVirtualThreadPerTaskExecutor();
        when(verifier.isAvailable()).thenReturn(true);
        when(embeddings.isAvailable()).thenReturn(false);
        service = new CodeVerificationService(search, verifier, embeddings, tokens::add, pool::execute, Duration.ofSeconds(25));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    @DisplayName("nothing found in the code answers NOT_FOUND without calling the model")
    void nothingFoundNeverCallsTheModel() {
        when(search.search(eq(PROJECT), anyString(), any(), any()))
                .thenReturn(new DeepSearchResult(List.of(), List.of("acme/reservas"), 40, false));

        CodeVerification result = service.verify(PROJECT, "pagar con Yape", SubjectKind.REQUIREMENT, "es",
                Duration.ofSeconds(25));

        assertThat(result.verdict()).isEqualTo(CodeVerdict.NOT_FOUND);
        assertThat(result.note()).contains("acme/reservas");
        assertThat(result.references()).isEmpty();
        verify(verifier, never()).verify(anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("found passages are read with exactly one model call")
    void oneModelCall() {
        when(search.search(eq(PROJECT), anyString(), any(), any()))
                .thenReturn(new DeepSearchResult(List.of(SNIPPET), List.of("acme/reservas"), 40, false));
        CodeVerification expected = new CodeVerification(CodeVerdict.ALREADY_EXISTS, "Ya existe", List.of(), 321);
        when(verifier.verify(anyString(), eq(SubjectKind.QUESTION), eq(List.of(SNIPPET)), eq("en"))).thenReturn(expected);

        CodeVerification result = service.verify(PROJECT, "does it?", SubjectKind.QUESTION, "en", Duration.ofSeconds(25));

        assertThat(result).isSameAs(expected);
        assertThat(tokens).containsExactly(321L);
        verify(verifier, times(1)).verify(anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a search that ran out of time with nothing found fails the verification")
    void searchTimeout() {
        when(search.search(eq(PROJECT), anyString(), any(), any()))
                .thenReturn(new DeepSearchResult(List.of(), List.of(), 0, true));

        assertThatThrownBy(() -> service.verify(PROJECT, "x", SubjectKind.REQUIREMENT, "es", Duration.ofSeconds(25)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.CODE_VERIFICATION_FAILED));
    }

    @Test
    @DisplayName("a model failure fails the verification instead of leaking the provider error")
    void modelFailure() {
        when(search.search(eq(PROJECT), anyString(), any(), any()))
                .thenReturn(new DeepSearchResult(List.of(SNIPPET), List.of("acme/reservas"), 40, false));
        when(verifier.verify(anyString(), any(), any(), anyString())).thenThrow(new IllegalStateException("429 quota"));

        assertThatThrownBy(() -> service.verify(PROJECT, "x", SubjectKind.REQUIREMENT, "es", Duration.ofSeconds(25)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.error()).isEqualTo(DiscoveryError.CODE_VERIFICATION_FAILED);
                    assertThat(e.getMessage()).doesNotContain("429");
                });
    }

    @Test
    @DisplayName("a model that does not answer within the budget fails the verification")
    void modelTimeout() {
        when(search.search(eq(PROJECT), anyString(), any(), any()))
                .thenReturn(new DeepSearchResult(List.of(SNIPPET), List.of("acme/reservas"), 40, false));
        when(verifier.verify(anyString(), any(), any(), anyString())).thenAnswer(inv -> {
            Thread.sleep(10_000);
            return null;
        });

        long started = System.nanoTime();
        assertThatThrownBy(() -> service.verify(PROJECT, "x", SubjectKind.REQUIREMENT, "es", Duration.ofMillis(2600)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getMessage()).contains("too long"));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("tokens of a call that finishes after the caller gave up are still metered")
    void lateTokensAreMetered() throws Exception {
        when(search.search(eq(PROJECT), anyString(), any(), any()))
                .thenReturn(new DeepSearchResult(List.of(SNIPPET), List.of("acme/reservas"), 40, false));
        when(verifier.verify(anyString(), any(), any(), anyString())).thenAnswer(inv -> {
            Thread.sleep(3_200);
            return new CodeVerification(CodeVerdict.UNSURE, "late", List.of(), 77);
        });

        assertThatThrownBy(() -> service.verify(PROJECT, "x", SubjectKind.REQUIREMENT, "es", Duration.ofMillis(2600)))
                .isInstanceOf(DomainException.class);
        for (int i = 0; i < 40 && tokens.isEmpty(); i++) Thread.sleep(100);

        assertThat(tokens).containsExactly(77L);
    }

    @Test
    @DisplayName("without a model nothing is searched")
    void noModel() {
        when(verifier.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service.verify(PROJECT, "x", SubjectKind.REQUIREMENT, "es", Duration.ofSeconds(25)))
                .isInstanceOf(DomainException.class);
        verify(search, never()).search(any(), anyString(), any(), any());
    }
}
