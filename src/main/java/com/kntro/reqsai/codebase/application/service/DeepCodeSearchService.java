package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.api.CodeLinks;
import com.kntro.reqsai.codebase.api.CodeModuleView;
import com.kntro.reqsai.codebase.api.CodeSnippet;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.codebase.api.DeepCodeSearchPort;
import com.kntro.reqsai.codebase.api.DeepSearchResult;
import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.ArchiveLimits;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RepositoryArchive;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * {@link DeepCodeSearchPort}: searches the pure source code of the project's repositories. For each ready
 * repository the archive at the indexed commit is fetched through the same access the indexer uses (an
 * installation token, or anonymously for a public repository) into a short-lived temporary file
 * ({@link ArchiveCache}), its source files are read with the indexer's filter and limits, credentials are
 * removed ({@link SecretRedactor}) and {@link CodeSearcher} picks the best passages, looking harder at the
 * modules the index already points at. The result is bounded in size and time and nothing is persisted.
 *
 * <p>Searching GitHub's own code search API would avoid the download, but it is rate limited per account,
 * indexes only the default branch and cannot be pinned to the indexed commit; it is a possible later
 * optimisation for very large repositories.
 */
@Service
@Slf4j
class DeepCodeSearchService implements DeepCodeSearchPort {

    private static final int RELEVANT_MODULES = 6;

    private final CodeRepositoryRepository repositories;
    private final CodeHostPort host;
    private final RepositoryAccess access;
    private final ArchiveCache cache;
    private final CodebaseModuleApi modules;
    private final ArchiveLimits limits;
    private final int maxSnippets;
    private final int maxSnippetChars;

    DeepCodeSearchService(CodeRepositoryRepository repositories, CodeHostPort host, RepositoryAccess access,
                          ArchiveCache cache, CodebaseModuleApi modules,
                          @Value("${reqsai.codebase.max-files:4000}") int maxFiles,
                          @Value("${reqsai.codebase.max-file-bytes:262144}") long maxFileBytes,
                          @Value("${reqsai.codebase.max-download-bytes:157286400}") long maxDownloadBytes,
                          @Value("${reqsai.codebase.deep-search.max-snippets:10}") int maxSnippets,
                          @Value("${reqsai.codebase.deep-search.max-snippet-chars:12000}") int maxSnippetChars) {
        this.repositories = repositories;
        this.host = host;
        this.access = access;
        this.cache = cache;
        this.modules = modules;
        this.limits = new ArchiveLimits(maxDownloadBytes, maxFiles, maxFileBytes);
        this.maxSnippets = Math.max(1, maxSnippets);
        this.maxSnippetChars = Math.max(1000, maxSnippetChars);
    }

    @Override
    public boolean hasReadyRepository(UUID projectId) {
        return !searchable(projectId).isEmpty();
    }

    @Override
    public DeepSearchResult search(UUID projectId, String queryText, float @Nullable [] queryEmbedding,
                                   Duration budget) {
        long started = System.nanoTime();
        long deadline = started + budget.toNanos();
        List<CodeRepository> targets = searchable(projectId);
        CodeSearcher.Query query = CodeSearcher.parse(queryText);
        if (targets.isEmpty() || query.isEmpty()) {
            return DeepSearchResult.empty();
        }
        Set<String> preferred = preferredPaths(projectId, queryEmbedding, queryText);

        List<CodeSnippet> found = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        int filesScanned = 0;
        boolean timedOut = false;
        RuntimeException lastFailure = null;
        for (CodeRepository repo : targets) {
            Duration remaining = Duration.ofNanos(deadline - System.nanoTime());
            if (remaining.isNegative() || remaining.isZero()) {
                timedOut = true;
                break;
            }
            try {
                RepositoryArchive archive = readArchive(repo, remaining);
                List<SourceFile> files = archive.files().stream()
                        .map(f -> new SourceFile(f.path(), SecretRedactor.redactPreservingLines(f.content())))
                        .toList();
                Set<String> prefixes = prefixesOf(repo, preferred);
                List<CodeSearcher.Passage> passages = CodeSearcher.search(files, query, prefixes, maxSnippets,
                        maxSnippetChars, () -> System.nanoTime() > deadline);
                for (CodeSearcher.Passage p : passages) {
                    found.add(new CodeSnippet(repo.fullName(), repo.getCommitSha(), p.path(), p.lineStart(),
                            p.lineEnd(), p.text(),
                            CodeLinks.blobUrl(repo.getHtmlUrl(), repo.getCommitSha(), p.path(), p.lineStart(),
                                    p.lineEnd()), p.score()));
                }
                searched.add(repo.fullName());
                filesScanned += files.size();
            } catch (TimeoutException e) {
                timedOut = true;
                log.info("Deep code search of {} ran out of time while downloading the archive", repo.fullName());
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("Deep code search could not read {}: {}", repo.fullName(), e.getMessage());
            }
        }
        if (searched.isEmpty() && lastFailure != null) {
            throw lastFailure;
        }
        if (searched.isEmpty() && timedOut) {
            throw CodebaseExceptions.hostUnavailable("the repository took too long to download");
        }
        found.sort(Comparator.comparingDouble(CodeSnippet::score).reversed());
        List<CodeSnippet> bounded = bound(found);
        log.info("Deep code search for project {}: {} file(s) in {}, {} snippet(s) ({} chars), {} ms{}",
                projectId, filesScanned, searched, bounded.size(),
                bounded.stream().mapToInt(s -> s.text().length()).sum(), (System.nanoTime() - started) / 1_000_000,
                timedOut ? " (time budget reached)" : "");
        return new DeepSearchResult(bounded, searched, filesScanned, timedOut);
    }

    private RepositoryArchive readArchive(CodeRepository repo, Duration remaining) throws TimeoutException {
        String token = access.tokenFor(repo.getInstallationId());
        String sha = repo.getCommitSha();
        String key = (RepositoryAccess.currentOrganization() + "|" + repo.fullName() + "@" + sha)
                .toLowerCase(Locale.ROOT);
        try (ArchiveCache.Lease lease = cache.acquire(key,
                () -> host.fetchArchive(repo.getOwner(), repo.getName(), sha, token, limits.maxDownloadBytes()),
                remaining)) {
            return host.readArchive(lease.path(), limits, SourceFilter::keep);
        }
    }

    /** At most the best snippets that fit the character budget, best first. */
    private List<CodeSnippet> bound(List<CodeSnippet> sorted) {
        List<CodeSnippet> out = new ArrayList<>();
        int chars = 0;
        for (CodeSnippet snippet : sorted) {
            if (out.size() >= maxSnippets || chars + snippet.text().length() > maxSnippetChars) continue;
            out.add(snippet);
            chars += snippet.text().length();
        }
        return out;
    }

    private List<CodeRepository> searchable(UUID projectId) {
        return repositories.findAllByProjectId(projectId).stream()
                .filter(r -> r.getCommitSha() != null && (r.getStatus() == CodeRepositoryStatus.READY
                        || r.getIndexedAt() != null))
                .toList();
    }

    /** Folders of the modules the index finds most related, as {@code repository:path}. */
    private Set<String> preferredPaths(UUID projectId, float @Nullable [] embedding, String text) {
        Set<String> out = new HashSet<>();
        try {
            for (CodeModuleView m : modules.findRelevantModules(projectId, embedding, text, RELEVANT_MODULES)) {
                if (!m.path().isEmpty()) out.add(m.repository().toLowerCase(Locale.ROOT) + ":" + m.path());
            }
        } catch (RuntimeException e) {
            log.debug("Deep code search could not read the related modules of project {}: {}", projectId,
                    e.getMessage());
        }
        return out;
    }

    private static Set<String> prefixesOf(CodeRepository repo, Set<String> preferred) {
        String head = repo.fullName().toLowerCase(Locale.ROOT) + ":";
        Set<String> out = new HashSet<>();
        for (String entry : preferred) {
            if (entry.startsWith(head)) out.add(entry.substring(head.length()));
        }
        return out;
    }
}
