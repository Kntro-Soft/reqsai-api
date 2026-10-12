package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.ArchiveLimits;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RepositoryArchive;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.ModuleDigest;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.ModuleSummary;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.OverviewDigest;
import com.kntro.reqsai.codebase.application.service.CodeIndexWriter.ModuleState;
import com.kntro.reqsai.codebase.application.service.CodeIndexWriter.RunTarget;
import com.kntro.reqsai.codebase.application.service.ModuleGrouper.ModuleDraft;
import com.kntro.reqsai.codebase.application.service.SymbolExtractor.FileSymbols;
import com.kntro.reqsai.codebase.domain.exception.CodebaseError;
import com.kntro.reqsai.codebase.domain.model.CodeModule;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * One indexing run of a connected repository: download the archive at the branch's head, keep the text
 * files worth reading, remove secrets, detect the stack, group the sources into modules, and describe
 * every module (AI summary, capabilities, implemented business rules, endpoints, entities) with an
 * embedding for similarity search. A module whose files did not change since the last run is kept as
 * is, so a reindex only pays for what changed. Without an AI model the modules still get a structural
 * description (symbols and endpoints) and the run is marked as not summarized.
 * <p>
 * Runs outside any transaction, in the tenant the launcher bound; modules are described a few at a time.
 */
@Component
@Slf4j
public class CodeIndexer {

    private static final int EXCERPT_LINES = 200;
    private static final int EXCERPT_FILE_CHARS = 5000;
    private static final int EXCERPT_MIN_FILE_CHARS = 1200;
    private static final int EXCERPT_TOTAL_CHARS = 18000;
    private static final int DIGEST_LIST_MAX = 40;
    private static final int README_CHARS = 4000;

    private static final Set<String> TELLING_NAMES = Set.of("controller", "service", "policy", "rule", "validator",
            "handler", "usecase", "use-case", "domain", "model", "entity", "route", "router", "page", "component",
            "api", "resource", "repository", "store", "manager", "config");

    private final CodeHostPort host;
    private final CodeSummaryPort summarizer;
    private final EmbeddingPort embeddingPort;
    private final RepositoryAccess access;
    private final CodeIndexWriter writer;
    private final int maxModules;
    private final int concurrency;
    private final ArchiveLimits limits;
    private final String summaryLanguage;

    public CodeIndexer(CodeHostPort host, CodeSummaryPort summarizer, EmbeddingPort embeddingPort,
                       RepositoryAccess access, CodeIndexWriter writer,
                       @Value("${reqsai.codebase.max-modules:80}") int maxModules,
                       @Value("${reqsai.codebase.summary-concurrency:4}") int concurrency,
                       @Value("${reqsai.codebase.max-files:4000}") int maxFiles,
                       @Value("${reqsai.codebase.max-file-bytes:262144}") long maxFileBytes,
                       @Value("${reqsai.codebase.max-download-bytes:157286400}") long maxDownloadBytes,
                       @Value("${reqsai.codebase.summary-language:Spanish}") String summaryLanguage) {
        this.host = host;
        this.summarizer = summarizer;
        this.embeddingPort = embeddingPort;
        this.access = access;
        this.writer = writer;
        this.maxModules = Math.max(1, maxModules);
        this.concurrency = Math.max(1, concurrency);
        this.limits = new ArchiveLimits(maxDownloadBytes, maxFiles, maxFileBytes);
        this.summaryLanguage = summaryLanguage;
    }

    public void index(java.util.UUID repositoryId) {
        RunTarget target = writer.start(repositoryId);
        if (target == null) {
            log.debug("Code index skipped: repository {} no longer exists", repositoryId);
            return;
        }
        String fullName = target.owner() + "/" + target.name();
        long started = System.nanoTime();
        try {
            String token = access.tokenFor(target.installationId());
            String sha = host.headCommit(target.owner(), target.name(), target.branch(), token);
            RepositoryArchive archive = host.download(target.owner(), target.name(), sha, token, limits,
                    SourceFilter::keep);
            List<SourceFile> files = archive.files().stream()
                    .map(f -> new SourceFile(f.path(), SecretRedactor.redact(f.content())))
                    .toList();
            CodeProfile profile = StackDetector.detect(files);
            List<SourceFile> sources = files.stream()
                    .filter(f -> SourceFilter.isSource(f.path()) || SourceFilter.isData(f.path()))
                    .toList();
            List<ModuleDraft> drafts = ModuleGrouper.group(sources, maxModules);
            writer.recordStructure(target.repositoryId(), sha, sources.size(), drafts.size(), profile);

            Map<String, ModuleState> existing = writer.existingModules(target.repositoryId());
            boolean ai = summarizer.isAvailable();
            AtomicBoolean allSummarized = new AtomicBoolean(ai);
            AtomicInteger done = new AtomicInteger();
            List<DescribedModule> described = describeAll(target, fullName, drafts, existing, ai, allSummarized, done);

            writer.keepOnly(target.repositoryId(), drafts.stream().map(ModuleDraft::path).toList());
            String overview = overview(fullName, files, described, ai);
            writer.markReady(target.repositoryId(), allSummarized.get(), profile.withOverview(overview));
            log.info("Code index of {} ready: {} files, {} modules ({} kept unchanged), AI summaries={}, {} ms",
                    fullName, sources.size(), drafts.size(), described.stream().filter(DescribedModule::unchanged).count(),
                    allSummarized.get(), (System.nanoTime() - started) / 1_000_000);
        } catch (DomainException e) {
            log.info("Code index of {} failed: {}", fullName, e.getMessage());
            writer.markFailed(target.repositoryId(), reasonFor(e));
        } catch (RuntimeException e) {
            log.warn("Code index of {} failed unexpectedly", fullName, e);
            writer.markFailed(target.repositoryId(), "No se pudo indexar el repositorio. Vuelve a intentarlo.");
        }
        if (writer.requeuePending(target.repositoryId())) {
            log.info("Code index of {}: a push arrived during the run, indexing again", fullName);
            index(repositoryId);
        }
    }

    private record DescribedModule(String path, String name, String summary, boolean unchanged) {
    }

    private List<DescribedModule> describeAll(RunTarget target, String fullName, List<ModuleDraft> drafts,
                                              Map<String, ModuleState> existing, boolean ai,
                                              AtomicBoolean allSummarized, AtomicInteger done) {
        TenantContext.TenantSnapshot tenant = TenantContext.capture();
        Semaphore permits = new Semaphore(concurrency);
        List<DescribedModule> described = java.util.Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (ModuleDraft draft : drafts) {
                futures.add(pool.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        TenantContext.runWith(tenant, () -> described.add(
                                describe(target, fullName, draft, existing.get(draft.path()), ai, allSummarized)));
                        int count = done.incrementAndGet();
                        TenantContext.runWith(tenant, () -> writer.progress(target.repositoryId(), count));
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException(cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Indexing interrupted", e);
                }
            }
        }
        return described;
    }

    private DescribedModule describe(RunTarget target, String fullName, ModuleDraft draft,
                                     @Nullable ModuleState previous, boolean ai, AtomicBoolean allSummarized) {
        String hash = contentHash(draft);
        if (previous != null && previous.contentHash().equals(hash) && (previous.summarized() || !ai)) {
            if (!previous.summarized()) allSummarized.set(false);
            return new DescribedModule(draft.path(), previous.name(), previous.summary(), true);
        }

        Set<String> symbols = new LinkedHashSet<>();
        Set<String> endpoints = new LinkedHashSet<>();
        Set<String> entities = new LinkedHashSet<>();
        for (SourceFile file : draft.files()) {
            FileSymbols found = SymbolExtractor.extract(file.path(), file.content());
            if (!SourceFilter.isTest(file.path())) symbols.addAll(found.symbols());
            endpoints.addAll(found.endpoints());
            entities.addAll(found.entities());
        }

        ModuleSummary summary = null;
        boolean summarized = false;
        if (ai) {
            try {
                summary = summarizer.summarizeModule(new ModuleDigest(fullName, draft.path(),
                        cap(draft.files().stream().map(SourceFile::path).toList()), cap(new ArrayList<>(symbols)),
                        cap(new ArrayList<>(endpoints)), cap(new ArrayList<>(entities)), excerpts(draft),
                        summaryLanguage));
                summarized = true;
            } catch (RuntimeException e) {
                log.info("Code summary of {}:{} fell back to the structural description: {}", fullName,
                        draft.path(), e.getMessage());
            }
        }
        if (summary == null) {
            summary = structural(draft, symbols, endpoints);
            allSummarized.set(false);
        }

        float[] embedding = embed(CodeModule.embeddingText(summary.name(), summary.summary(), summary.capabilities(),
                summary.businessRules()));
        writer.saveModule(target.repositoryId(), target.projectId(), draft.path(), summary.name(), summary.summary(),
                summary.capabilities(), summary.businessRules(), cap(new ArrayList<>(endpoints)),
                cap(new ArrayList<>(entities)), draft.files().size(), hash, summarized, embedding);
        return new DescribedModule(draft.path(), summary.name(), summary.summary(), false);
    }

    /** What the copilot knows about a module without the AI: where it is, what it declares and exposes. */
    static ModuleSummary structural(ModuleDraft draft, Set<String> symbols, Set<String> endpoints) {
        String name = draft.path().isEmpty() ? "Raíz del repositorio" : humanize(lastSegment(draft.path()));
        String declared = symbols.stream().limit(8).collect(Collectors.joining(", "));
        String summary = "Carpeta " + (draft.path().isEmpty() ? "raíz" : draft.path()) + " con "
                + draft.files().size() + " archivo(s)" + (declared.isEmpty() ? "." : ": " + declared + ".");
        List<String> capabilities = endpoints.stream().limit(10).toList();
        return new ModuleSummary(name, summary, capabilities, List.of());
    }

    /**
     * Excerpts of the module's files for the AI, most telling first. Every file gets a share of the budget (a
     * folder of components holds one feature per file, and none should go unread); a module with few files gives
     * each one more room.
     */
    static String excerpts(ModuleDraft draft) {
        List<SourceFile> ranked = new ArrayList<>(draft.files());
        ranked.sort(Comparator.comparingInt((SourceFile f) -> rank(f)).reversed()
                .thenComparing(SourceFile::path));
        int perFile = Math.max(EXCERPT_MIN_FILE_CHARS,
                Math.min(EXCERPT_FILE_CHARS, EXCERPT_TOTAL_CHARS / Math.max(1, ranked.size())));
        StringBuilder sb = new StringBuilder();
        for (SourceFile file : ranked) {
            int room = EXCERPT_TOTAL_CHARS - sb.length();
            if (room <= 200) break;
            String body = SourceFilter.isData(file.path())
                    ? dataExcerpt(file)
                    : file.content().lines().limit(EXCERPT_LINES).collect(Collectors.joining("\n"));
            int cap = Math.min(perFile, room - 40);
            if (body.length() > cap) body = body.substring(0, Math.max(0, cap)) + "\n…";
            sb.append("// FILE: ").append(file.path()).append("\n").append(body).append("\n\n");
        }
        return sb.toString().strip();
    }

    /** The business values of a data file: flattened JSON, or the value-bearing lines of YAML. */
    static String dataExcerpt(SourceFile file) {
        if (file.path().toLowerCase(Locale.ROOT).endsWith(".json")) {
            String flat = DataDigest.flatten(file.content());
            if (flat != null) return flat;
        }
        return salientLines(file.content());
    }

    /** Words that mark a line of a data file as carrying business values (prices, plans, limits, times). */
    private static final java.util.regex.Pattern SALIENT = java.util.regex.Pattern.compile(
            "\\d|(?i)[\"']?(?:name|nombre|title|t[ií]tulo|id|plan|price|precio|cost|costo|fee|tarifa|discount"
                    + "|descuento|limit|l[ií]mite|max|min|monthly|mensual|annual|anual|currency|moneda)[\"']?\\s*:");

    /**
     * The value-bearing lines of a data file (translations, catalogs, configuration) with the line before each
     * as context: a 30 KB translation file is mostly prose, while its prices and limits sit in a few lines.
     */
    static String salientLines(String content) {
        List<String> lines = content.lines().toList();
        StringBuilder sb = new StringBuilder();
        int lastKept = -2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || !SALIENT.matcher(line).find()) continue;
            if (i - 1 > lastKept && i > 0) sb.append(lines.get(i - 1).strip()).append('\n');
            sb.append(line.strip()).append('\n');
            lastKept = i;
        }
        return sb.isEmpty() ? content : sb.toString();
    }

    private static int rank(SourceFile file) {
        String lower = file.path().toLowerCase(Locale.ROOT);
        int score = SourceFilter.isTest(lower) ? -10 : 0;
        for (String telling : TELLING_NAMES) {
            if (lower.contains(telling)) score += 3;
        }
        FileSymbols symbols = SymbolExtractor.extract(file.path(), file.content());
        score += Math.min(6, symbols.endpoints().size() * 2 + symbols.entities().size() * 2);
        score += Math.min(4, file.content().length() / 2000);
        return score;
    }

    private @Nullable String overview(String fullName, List<SourceFile> files, List<DescribedModule> described,
                                      boolean ai) {
        String readme = files.stream()
                .filter(f -> SourceFilter.isReadme(f.path()))
                .min(Comparator.comparingInt((SourceFile f) -> f.path().split("/").length).thenComparing(SourceFile::path))
                .map(f -> f.content().length() > README_CHARS ? f.content().substring(0, README_CHARS) : f.content())
                .orElse(null);
        if (ai) {
            List<String> lines = described.stream()
                    .sorted(Comparator.comparing(DescribedModule::path))
                    .limit(DIGEST_LIST_MAX)
                    .map(d -> "- " + d.name() + ": " + d.summary())
                    .toList();
            try {
                String text = summarizer.summarizeOverview(new OverviewDigest(fullName, readme, lines, summaryLanguage));
                if (text != null) return text;
            } catch (RuntimeException e) {
                log.info("Code overview of {} skipped: {}", fullName, e.getMessage());
            }
        }
        return readme == null ? null : firstParagraph(readme);
    }

    static @Nullable String firstParagraph(String readme) {
        for (String block : readme.split("\\n\\s*\\n")) {
            String text = block.replaceAll("(?m)^#+\\s*", "").replaceAll("[*_`>\\[\\]]", "").strip();
            if (text.length() >= 40 && !text.startsWith("!") && !text.startsWith("<")) {
                return text.length() > 600 ? text.substring(0, 597) + "..." : text;
            }
        }
        return null;
    }

    private float @Nullable [] embed(String text) {
        if (!embeddingPort.isAvailable()) return null;
        try {
            return embeddingPort.embed(text);
        } catch (RuntimeException e) {
            log.debug("Code module embedding skipped: {}", e.getMessage());
            return null;
        }
    }

    static String contentHash(ModuleDraft draft) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(draft.path().getBytes(StandardCharsets.UTF_8));
            for (SourceFile file : draft.files()) {
                digest.update((byte) 0);
                digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.content().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The message the team sees on a failed run. */
    static String reasonFor(DomainException e) {
        String code = e.error() == null ? "" : e.error().code();
        if (CodebaseError.CODE_REPOSITORY_NOT_FOUND.code().equals(code)) {
            return "No se encontró el repositorio o la rama. Si es privado, conecta GitHub en Ajustes → Integraciones"
                    + " y dale acceso a ReqsAI.";
        }
        if (CodebaseError.CODE_REPOSITORY_ACCESS_DENIED.code().equals(code)) {
            return "GitHub no permite a ReqsAI leer este repositorio; revisa su acceso en la app de GitHub.";
        }
        if (CodebaseError.CODE_REPOSITORY_TOO_LARGE.code().equals(code)) {
            return "El repositorio es demasiado grande para indexarlo.";
        }
        if (CodebaseError.CODE_HOST_INSTALLATION_NOT_FOUND.code().equals(code)) {
            return "La organización ya no tiene conectada la app de GitHub que lee este repositorio.";
        }
        if (CodebaseError.CODE_HOST_UNAVAILABLE.code().equals(code)) {
            return "GitHub no respondió o alcanzó su límite de uso; vuelve a intentarlo en unos minutos.";
        }
        return "No se pudo indexar el repositorio. Vuelve a intentarlo.";
    }

    private static List<String> cap(List<String> values) {
        return values.size() <= DIGEST_LIST_MAX ? values : values.subList(0, DIGEST_LIST_MAX);
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String humanize(String segment) {
        String words = segment.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("[_\\-.]+", " ").strip();
        if (words.isEmpty()) return segment;
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
