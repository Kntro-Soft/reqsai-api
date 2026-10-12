package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.codebase.api.DeepCodeSearchPort;
import com.kntro.reqsai.codebase.api.DeepSearchResult;
import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort.SubjectKind;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The deep path of the code-aware copilot. When the module index finds nothing or is unsure, or when the analyst
 * asks, it searches the PURE source code of the connected repositories ({@link DeepCodeSearchPort}) and has the
 * model read the best passages once ({@link CodeVerificationPort}): at most one model call per verification, all
 * within a time budget. A search that finds no code at all answers {@code NOT_FOUND} without calling the model.
 * Nothing about the code is stored here; the caller keeps the verdict and the references.
 */
@Service
@Slf4j
public class CodeVerificationService {

    /** Time kept for the model call out of the whole budget, so a slow download cannot starve it. */
    private static final Duration MODEL_RESERVE = Duration.ofSeconds(8);
    private static final Duration MIN_SEARCH = Duration.ofSeconds(3);
    private static final Duration MIN_MODEL = Duration.ofSeconds(2);

    private final DeepCodeSearchPort search;
    private final CodeVerificationPort verifier;
    private final EmbeddingPort embeddingPort;
    private final TaskExecutor executor;
    private final Duration defaultBudget;

    public CodeVerificationService(DeepCodeSearchPort search, CodeVerificationPort verifier,
                                   EmbeddingPort embeddingPort, @Qualifier("taskExecutor") TaskExecutor executor,
                                   @Value("${discovery.code-verification.budget:PT25S}") Duration defaultBudget) {
        this.search = search;
        this.verifier = verifier;
        this.embeddingPort = embeddingPort;
        this.executor = executor;
        this.defaultBudget = defaultBudget;
    }

    /** The whole time a verification may take. */
    public Duration budget() {
        return defaultBudget;
    }

    public boolean hasReadyRepository(UUID projectId) {
        return search.hasReadyRepository(projectId);
    }

    public boolean isAvailable() {
        return verifier.isAvailable();
    }

    /**
     * Verifies {@code subject} against the project's code.
     *
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException when the code host or the model fails or
     *                                                                   the budget runs out
     */
    public CodeVerification verify(UUID projectId, String subject, SubjectKind kind, String language,
                                   Duration budget) {
        long started = System.nanoTime();
        long deadline = started + budget.toNanos();
        if (!verifier.isAvailable()) {
            throw DiscoveryExceptions.codeVerificationFailed("the AI model is not available");
        }
        float[] embedding = tryEmbed(subject);
        Duration searchBudget = max(MIN_SEARCH, min(remaining(deadline).minus(MODEL_RESERVE), remaining(deadline)));
        DeepSearchResult found = search.search(projectId, subject, embedding, searchBudget);
        if (found.snippets().isEmpty()) {
            if (found.timedOut()) {
                throw DiscoveryExceptions.codeVerificationFailed("the code search ran out of time");
            }
            log.info("Deep verification for project {}: nothing in the code relates to the request ({} files, {} ms)",
                    projectId, found.filesScanned(), millisSince(started));
            return new CodeVerification(CodeVerdict.NOT_FOUND, notFoundNote(language, found), List.of());
        }
        Duration left = remaining(deadline);
        if (left.compareTo(MIN_MODEL) < 0) {
            throw DiscoveryExceptions.codeVerificationFailed("ran out of time before reading the code");
        }
        CodeVerification result = readWithTimeout(subject, kind, found, language, left);
        log.info("Deep verification for project {}: verdict={}, {} snippet(s) read, {} reference(s), {} ms",
                projectId, result.verdict(), found.snippets().size(), result.references().size(),
                millisSince(started));
        return result;
    }

    private CodeVerification readWithTimeout(String subject, SubjectKind kind, DeepSearchResult found,
                                             String language, Duration timeout) {
        TenantContext.TenantSnapshot tenant = TenantContext.capture();
        CompletableFuture<CodeVerification> future = new CompletableFuture<>();
        executor.execute(() -> TenantContext.runWith(tenant, () -> {
            try {
                future.complete(verifier.verify(subject, kind, found.snippets(), language));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }));
        try {
            return future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw DiscoveryExceptions.codeVerificationFailed("the AI model took too long");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("Deep verification: the model call failed: {}", cause == null ? e.getMessage() : cause.getMessage());
            throw DiscoveryExceptions.codeVerificationFailed("the AI model could not read the code");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DiscoveryExceptions.codeVerificationFailed("interrupted");
        }
    }

    private float @Nullable [] tryEmbed(String text) {
        try {
            return embeddingPort.isAvailable() ? embeddingPort.embed(text) : null;
        } catch (RuntimeException e) {
            log.debug("Deep verification continues without embedding: {}", e.getMessage());
            return null;
        }
    }

    static String notFoundNote(String language, DeepSearchResult found) {
        boolean english = language != null && language.toLowerCase().startsWith("en");
        String repos = String.join(", ", found.repositories());
        return english
                ? "I searched the code of " + repos + " and found nothing related to this."
                : "Busqué en el código de " + repos + " y no encontré nada relacionado con esto.";
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        return nanos <= 0 ? Duration.ZERO : Duration.ofNanos(nanos);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static long millisSince(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
