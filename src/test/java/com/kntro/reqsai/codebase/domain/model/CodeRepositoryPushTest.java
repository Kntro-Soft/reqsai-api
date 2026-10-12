package com.kntro.reqsai.codebase.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodeRepository pushes")
class CodeRepositoryPushTest {

    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");

    private CodeRepository ready(String sha) {
        CodeRepository repo = CodeRepository.connect(UUID.randomUUID(), "Acme", "Reservas", "main",
                "https://github.com/acme/reservas", true, 1001L, now);
        repo.startIndexing(now);
        repo.recordStructure(sha, 10, 2, CodeProfile.empty(), now);
        repo.markReady(true, CodeProfile.empty(), now);
        return repo;
    }

    @Test
    @DisplayName("tracks its own branch only, ignoring the case of owner and name")
    void tracks() {
        CodeRepository repo = ready("aaa111");
        assertThat(repo.viaApp()).isTrue();
        assertThat(repo.tracks("acme", "reservas", "main")).isTrue();
        assertThat(repo.tracks("acme", "reservas", "develop")).isFalse();
        assertThat(repo.tracks("acme", "otro", "main")).isFalse();
    }

    @Test
    @DisplayName("a new commit starts a run; the indexed one does nothing")
    void push() {
        CodeRepository repo = ready("aaa111");
        assertThat(repo.acceptPush("aaa111", now)).isFalse();
        assertThat(repo.acceptPush("bbb222", now)).isTrue();
        assertThat(repo.getStatus()).isEqualTo(CodeRepositoryStatus.PENDING);
    }

    @Test
    @DisplayName("a push during a run waits for it to end, then runs once")
    void pushDuringRun() {
        CodeRepository repo = ready("aaa111");
        repo.startIndexing(now);
        assertThat(repo.acceptPush("ccc333", now)).isFalse();
        repo.recordStructure("bbb222", 10, 2, CodeProfile.empty(), now);
        repo.markReady(true, CodeProfile.empty(), now);
        assertThat(repo.takePendingCommit()).isEqualTo("ccc333");
        assertThat(repo.takePendingCommit()).isNull();
    }

    @Test
    @DisplayName("revoked access fails the repository with the reason and drops a pending push")
    void revoke() {
        CodeRepository repo = ready("aaa111");
        repo.startIndexing(now);
        repo.acceptPush("ccc333", now);
        repo.revokeAccess("Este repositorio ya no está compartido con ReqsAI", now);
        assertThat(repo.getStatus()).isEqualTo(CodeRepositoryStatus.FAILED);
        assertThat(repo.getError()).contains("ya no está compartido");
        assertThat(repo.takePendingCommit()).isNull();
    }
}
