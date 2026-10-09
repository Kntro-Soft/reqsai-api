package com.kntro.reqsai.codebase.infrastructure.github;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.ArchiveLimits;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RemoteRepository;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RepositoryArchive;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.service.SourceFilter;
import com.kntro.reqsai.codebase.domain.exception.CodebaseError;
import com.kntro.reqsai.codebase.support.FakeGitHub;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The GitHub adapter against a local fake of the REST API: metadata, branch head, zipball and errors. */
@DisplayName("Infra: GitHub code host")
class GitHubCodeHostAdapterTest {

    private FakeGitHub github;
    private GitHubCodeHostAdapter adapter;

    @BeforeEach
    void start() throws Exception {
        github = new FakeGitHub();
        github.put("acme", "reservas", "main", false, "", FakeGitHub.restaurantApp());
        github.put("acme", "privado", "develop", true, "good-token", Map.of("src/a.ts", "export const A = 1;"));
        adapter = new GitHubCodeHostAdapter(github.apiUrl(), Duration.ofSeconds(10), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        github.close();
    }

    @Test
    @DisplayName("describes a public repository and resolves its branch head")
    void describes() {
        RemoteRepository repo = adapter.describe("ACME", "Reservas", null);
        assertThat(repo.owner()).isEqualTo("acme");
        assertThat(repo.name()).isEqualTo("reservas");
        assertThat(repo.defaultBranch()).isEqualTo("main");
        assertThat(repo.isPrivate()).isFalse();
        assertThat(adapter.headCommit("acme", "reservas", "main", null)).matches("[0-9a-f]{7,64}");
    }

    @Test
    @DisplayName("downloads the zipball through the redirect and keeps only the files worth reading")
    void downloads() {
        String sha = adapter.headCommit("acme", "reservas", "main", null);
        RepositoryArchive archive = adapter.download("acme", "reservas", sha, null,
                new ArchiveLimits(10_000_000, 100, 100_000), SourceFilter::keep);

        assertThat(archive.files()).extracting(SourceFile::path)
                .contains("README.md", "package.json", "src/reservations/cancellation.policy.ts",
                        "db/migrations/001_init.sql")
                .doesNotContain(".env", "package-lock.json", "node_modules/express/index.js");
        assertThat(archive.skippedFiles()).isGreaterThanOrEqualTo(3);
        assertThat(archive.truncated()).isFalse();
    }

    @Test
    @DisplayName("stops at the file limit and says the archive was truncated")
    void truncates() {
        RepositoryArchive archive = adapter.download("acme", "reservas", "x", null,
                new ArchiveLimits(10_000_000, 2, 100_000), SourceFilter::keep);
        assertThat(archive.files()).hasSize(2);
        assertThat(archive.truncated()).isTrue();
    }

    @Test
    @DisplayName("a private repository needs its token; a wrong token and an unknown branch are refused")
    void privateRepositories() {
        assertThatThrownBy(() -> adapter.describe("acme", "privado", null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(CodebaseError.CODE_REPOSITORY_NOT_FOUND));
        assertThatThrownBy(() -> adapter.describe("acme", "privado", "wrong"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(CodebaseError.CODE_REPOSITORY_ACCESS_DENIED));
        assertThat(adapter.describe("acme", "privado", "good-token").isPrivate()).isTrue();
        assertThatThrownBy(() -> adapter.headCommit("acme", "privado", "main", "good-token"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(CodebaseError.CODE_REPOSITORY_NOT_FOUND));
    }

    @Test
    @DisplayName("an archive over the download cap is refused as too large")
    void tooLarge() {
        assertThatThrownBy(() -> adapter.download("acme", "reservas", "x", null,
                new ArchiveLimits(100, 100, 100_000), SourceFilter::keep))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(CodebaseError.CODE_REPOSITORY_TOO_LARGE));
    }

    @Test
    @DisplayName("strips the zipball root folder from entry names")
    void stripRoot() {
        assertThat(GitHubCodeHostAdapter.stripRoot("acme-reservas-1a2b3c4/src/app.ts")).isEqualTo("src/app.ts");
        assertThat(GitHubCodeHostAdapter.stripRoot("acme-reservas-1a2b3c4/")).isEmpty();
    }
}
