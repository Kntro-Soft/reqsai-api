package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.api.CodeModuleView;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.codebase.api.DeepSearchResult;
import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RepositoryArchive;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Codebase: deep code search")
class DeepCodeSearchServiceTest {

    private static final String STRIPE = "sk_" + "live_" + "abcdefghijklmnopqrstuvwx";
    private static final UUID PROJECT = UUID.randomUUID();

    private final CodeRepositoryRepository repositories = mock(CodeRepositoryRepository.class);
    private final CodeHostPort host = mock(CodeHostPort.class);
    private final RepositoryAccess access = mock(RepositoryAccess.class);
    private final CodebaseModuleApi modules = mock(CodebaseModuleApi.class);
    private final List<Path> temp = new ArrayList<>();
    private ArchiveCache cache;
    private DeepCodeSearchService service;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.setCurrentTenant(UUID.randomUUID().toString());
        cache = new ArchiveCache(Runnable::run, Duration.ofMinutes(10), 1_000_000, 4);
        service = new DeepCodeSearchService(repositories, host, access, cache, modules, 4000, 262144, 157286400, 10, 12000);
        Path zip = Files.createTempFile("deep-search-test-", ".zip");
        Files.write(zip, new byte[16]);
        temp.add(zip);
        when(host.fetchArchive(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(zip);
        when(modules.findRelevantModules(any(), any(), anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws IOException {
        TenantContext.clear();
        cache.shutdown();
        for (Path p : temp) Files.deleteIfExists(p);
    }

    private CodeRepository readyRepository() {
        CodeRepository repo = CodeRepository.connect(PROJECT, "acme", "reservas", "main",
                "https://github.com/acme/reservas", false, null, Instant.now());
        repo.recordStructure("abc1234", 3, 1, CodeProfile.empty(), Instant.now());
        repo.markReady(true, CodeProfile.empty(), Instant.now());
        return repo;
    }

    private void archive(SourceFile... files) {
        when(host.readArchive(any(), any(), any())).thenReturn(new RepositoryArchive(List.of(files), 0, false));
    }

    @Test
    @DisplayName("finds the passage with its file, lines and a link to the indexed commit, secrets removed")
    void findsWithLinksAndRedaction() {
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of(readyRepository()));
        archive(new SourceFile("src/reservations/cancellation.policy.ts", """
                // header
                const apiKey = "%s";
                export const CANCELLATION_LIMIT_HOURS = 2;
                """.formatted(STRIPE)), new SourceFile("src/menu/dishes.ts", "export const DISHES = [];"));

        DeepSearchResult result = service.search(PROJECT, "cancelar la reserva hasta 24 horas antes", null,
                Duration.ofSeconds(10));

        assertThat(result.repositories()).containsExactly("acme/reservas");
        assertThat(result.filesScanned()).isEqualTo(2);
        var snippet = result.snippets().getFirst();
        assertThat(snippet.path()).isEqualTo("src/reservations/cancellation.policy.ts");
        assertThat(snippet.commitSha()).isEqualTo("abc1234");
        assertThat(snippet.text()).contains("CANCELLATION_LIMIT_HOURS = 2").doesNotContain(STRIPE).contains("[REDACTED]");
        assertThat(snippet.url()).startsWith("https://github.com/acme/reservas/blob/abc1234/src/reservations/cancellation.policy.ts#L");
        assertThat(snippet.lineStart()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("the archive is fetched once for the same commit, and the indexed commit is the one read")
    void cachedPerCommit() {
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of(readyRepository()));
        archive(new SourceFile("src/a.ts", "export function cancelReservation() {}"));

        service.search(PROJECT, "cancel reservation", null, Duration.ofSeconds(10));
        service.search(PROJECT, "reservation cancel", null, Duration.ofSeconds(10));

        verify(host, times(1)).fetchArchive(eq("acme"), eq("reservas"), eq("abc1234"), any(), anyLong());
        verify(host, times(2)).readArchive(any(), any(), any());
    }

    @Test
    @DisplayName("the modules the index points at are searched first")
    void prefersRelatedModules() {
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of(readyRepository()));
        when(modules.findRelevantModules(any(), any(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new CodeModuleView(UUID.randomUUID(), "acme/reservas", "src/booking", "Reservas",
                        "s", List.of(), List.of(), List.of(), null)));
        archive(new SourceFile("src/legacy/cancel.ts", "export function cancelReservation() {}"),
                new SourceFile("src/booking/cancel.ts", "export function cancelReservation() {}"));

        DeepSearchResult result = service.search(PROJECT, "cancel reservation", null, Duration.ofSeconds(10));

        assertThat(result.snippets().getFirst().path()).isEqualTo("src/booking/cancel.ts");
    }

    @Test
    @DisplayName("a project without an indexed repository finds nothing and says it has none")
    void noRepository() {
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of());

        assertThat(service.hasReadyRepository(PROJECT)).isFalse();
        assertThat(service.search(PROJECT, "cancel", null, Duration.ofSeconds(10)).snippets()).isEmpty();
    }

    @Test
    @DisplayName("a repository that is still being indexed for the first time cannot be searched")
    void pendingRepository() {
        CodeRepository pending = CodeRepository.connect(PROJECT, "acme", "reservas", "main",
                "https://github.com/acme/reservas", false, null, Instant.now());
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of(pending));

        assertThat(service.hasReadyRepository(PROJECT)).isFalse();
    }

    @Test
    @DisplayName("when GitHub cannot be reached the failure surfaces instead of an empty answer")
    void hostFailure() {
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of(readyRepository()));
        when(host.fetchArchive(anyString(), anyString(), anyString(), any(), anyLong()))
                .thenThrow(CodebaseExceptions.hostUnavailable("down"));

        assertThatThrownBy(() -> service.search(PROJECT, "cancel reservation", null, Duration.ofSeconds(10)))
                .hasMessageContaining("down");
    }

    @Test
    @DisplayName("the snippets stay within the size and count limits")
    void bounded() {
        when(repositories.findAllByProjectId(PROJECT)).thenReturn(List.of(readyRepository()));
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            files.add(new SourceFile("src/f" + i + ".ts", ("cancel reservation line\n").repeat(200)));
        }
        when(host.readArchive(any(), any(), any())).thenReturn(new RepositoryArchive(files, 0, false));

        DeepSearchResult result = service.search(PROJECT, "cancel reservation", null, Duration.ofSeconds(10));

        assertThat(result.snippets()).hasSizeLessThanOrEqualTo(10);
        assertThat(result.snippets().stream().mapToInt(s -> s.text().length()).sum()).isLessThanOrEqualTo(12000);
    }
}
