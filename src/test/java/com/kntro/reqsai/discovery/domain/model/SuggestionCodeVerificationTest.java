package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.SuggestionCodeVerifiedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Suggestion: deep verification against the code")
class SuggestionCodeVerificationTest {

    private static final Duration STALE = Duration.ofMinutes(2);

    private static Suggestion pending() {
        Suggestion s = Suggestion.newStory(UUID.randomUUID(), UUID.randomUUID(), "Cancelar reserva", "comensal",
                "cancelar mi reserva", "no perder dinero", Priority.HIGH, 3);
        ReflectionTestUtils.invokeMethod(s, "clearDomainEvents");
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> events(Suggestion s) {
        return (Collection<Object>) ReflectionTestUtils.invokeMethod(s, "domainEvents");
    }

    @Test
    @DisplayName("asking to verify marks it VERIFYING and announces it")
    void begin() {
        Suggestion s = pending();

        assertThat(s.beginCodeVerification(Instant.now(), STALE)).isTrue();

        assertThat(s.getCodeVerifyState()).isEqualTo(CodeVerifyState.VERIFYING);
        assertThat(events(s)).singleElement().isInstanceOf(SuggestionCodeVerifiedEvent.class);
    }

    @Test
    @DisplayName("asking again while a recent verification runs starts nothing; a lost one restarts")
    void beginWhileRunning() {
        Suggestion s = pending();
        ReflectionTestUtils.setField(s, "updatedAt", Instant.now());
        s.beginCodeVerification(Instant.now(), STALE);
        ReflectionTestUtils.setField(s, "updatedAt", Instant.now());

        assertThat(s.beginCodeVerification(Instant.now(), STALE)).isFalse();
        assertThat(s.beginCodeVerification(Instant.now().plus(STALE).plusSeconds(1), STALE)).isTrue();
    }

    @Test
    @DisplayName("a verdict that finds the capability sets the finding, note and references and clears errors")
    void complete() {
        Suggestion s = pending();
        s.failCodeVerification("timeout");
        CodeReference ref = new CodeReference("acme/reservas", "src/reservations", "cancellation.policy.ts",
                "https://github.com/acme/reservas/blob/abc/src/reservations/cancellation.policy.ts#L3-L4",
                "src/reservations/cancellation.policy.ts", 3, 4, "LIMIT = 2");
        Instant now = Instant.now();

        s.completeCodeVerification(CodeVerdict.CONFLICTS_WITH_CODE, "El código permite 2 h", List.of(ref), now);

        assertThat(s.getCodeVerifyState()).isEqualTo(CodeVerifyState.VERIFIED);
        assertThat(s.getCodeVerdict()).isEqualTo(CodeVerdict.CONFLICTS_WITH_CODE);
        assertThat(s.getCodeFinding()).isEqualTo(CodeFinding.CONFLICTS_WITH_CODE);
        assertThat(s.getCodeNote()).isEqualTo("El código permite 2 h");
        assertThat(s.getCodeReferences()).containsExactly(ref);
        assertThat(s.getCodeVerifiedAt()).isEqualTo(now);
        assertThat(s.getCodeVerifyError()).isNull();
    }

    @Test
    @DisplayName("NOT_FOUND replaces an earlier quick finding with no finding")
    void notFoundClearsFinding() {
        Suggestion s = pending();
        s.annotate(1, "q", CodeFinding.ALREADY_EXISTS, "ya existe", List.of());

        s.completeCodeVerification(CodeVerdict.NOT_FOUND, "Nada en el código", List.of(), Instant.now());

        assertThat(s.getCodeFinding()).isNull();
        assertThat(s.getCodeVerdict()).isEqualTo(CodeVerdict.NOT_FOUND);
        assertThat(s.getCodeNote()).isEqualTo("Nada en el código");
    }

    @Test
    @DisplayName("a failure keeps the reason (clipped) and allows a retry")
    void fail() {
        Suggestion s = pending();
        s.beginCodeVerification(Instant.now(), STALE);

        s.failCodeVerification("x".repeat(500));

        assertThat(s.getCodeVerifyState()).isEqualTo(CodeVerifyState.FAILED);
        assertThat(s.getCodeVerifyError()).hasSize(Suggestion.VERIFY_ERROR_MAX).endsWith("...");
        assertThat(s.beginCodeVerification(Instant.now(), STALE)).isTrue();
        assertThat(s.getCodeVerifyError()).isNull();
    }

    @Test
    @DisplayName("only a pending suggestion can be verified")
    void resolvedCannotBeVerified() {
        Suggestion s = pending();
        s.dismiss();

        assertThatThrownBy(() -> s.beginCodeVerification(Instant.now(), STALE))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.SUGGESTION_ALREADY_RESOLVED));
    }

    @Test
    @DisplayName("old references without file or lines still read")
    void oldReferencesStillRead() {
        CodeReference old = new CodeReference("acme/reservas", "src/reservations", "Reservas", "https://x");

        assertThat(old.hasLines()).isFalse();
        assertThat(old.file()).isNull();
    }
}
