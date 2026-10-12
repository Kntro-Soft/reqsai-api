package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.SetupStepView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Application: the repositories step of a project's setup")
class CodeSetupParticipantTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private static CodeRepository repo(String name) {
        return CodeRepository.connect(PROJECT, "acme", name, "main", "https://github.com/acme/" + name, false, null, NOW);
    }

    private static CodeRepository indexing(String name, int done, int total) {
        CodeRepository repo = repo(name);
        repo.startIndexing(NOW);
        repo.recordStructure("abc1234", 10, total, CodeProfile.empty(), NOW);
        repo.recordProgress(done, NOW);
        return repo;
    }

    private static CodeRepository ready(String name, CodeProfile profile) {
        CodeRepository repo = indexing(name, 2, 2);
        repo.markReady(true, profile, NOW);
        return repo;
    }

    private static CodeRepository failed(String name) {
        CodeRepository repo = repo(name);
        repo.startIndexing(NOW);
        repo.markFailed("GitHub no respondió", NOW);
        return repo;
    }

    @Test
    @DisplayName("nothing connected yet: no step to show")
    void none() {
        assertThat(CodeSetupParticipant.stepOf(List.of())).isEmpty();
    }

    @Test
    @DisplayName("running while any repository indexes, with the modules counted across repositories")
    void running() {
        SetupStepView step = CodeSetupParticipant.stepOf(List.of(indexing("a", 3, 7), ready("b", CodeProfile.empty())))
                .orElseThrow();

        assertThat(step.key()).isEqualTo(SetupStepKeys.GITHUB_REPOS);
        assertThat(step.status()).isEqualTo(SetupStepStatus.RUNNING);
        assertThat(step.done()).isEqualTo(5);
        assertThat(step.total()).isEqualTo(9);
    }

    @Test
    @DisplayName("failed once every repository stopped and one failed, naming it")
    void failedStep() {
        SetupStepView step = CodeSetupParticipant.stepOf(List.of(ready("a", CodeProfile.empty()), failed("b")))
                .orElseThrow();

        assertThat(step.status()).isEqualTo(SetupStepStatus.FAILED);
        assertThat(step.detail()).startsWith("acme/b");
    }

    @Test
    @DisplayName("done when every repository is ready")
    void done() {
        SetupStepView step = CodeSetupParticipant.stepOf(List.of(ready("a", CodeProfile.empty()))).orElseThrow();

        assertThat(step.status()).isEqualTo(SetupStepStatus.DONE);
        assertThat(step.done()).isEqualTo(step.total());
    }

    @Test
    @DisplayName("the stack merges the ready repositories without repeats, ignoring case and accents")
    void stack() {
        var one = ready("a", new CodeProfile(List.of("TypeScript"), List.of("Angular"), List.of("PostgreSQL"),
                List.of("Web"), null));
        var two = ready("b", new CodeProfile(List.of("typescript", "Java"), List.of("Spring Boot"),
                List.of("postgresql"), List.of("Móvil"), null));
        var notReady = indexing("c", 0, 4);

        var stack = CodeSetupParticipant.stackOf(List.of(one, two, notReady));

        assertThat(stack.languages()).containsExactly("TypeScript", "Java");
        assertThat(stack.frameworks()).containsExactly("Angular", "Spring Boot");
        assertThat(stack.databases()).containsExactly("PostgreSQL");
        assertThat(stack.platforms()).containsExactly("Web", "Móvil");
        assertThat(CodeSetupParticipant.stackOf(List.of(notReady)).isEmpty()).isTrue();
    }
}
