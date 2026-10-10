package com.kntro.reqsai.codebase.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A source repository the client connected to a project. Indexing turns it into {@link CodeModule}s;
 * this aggregate tracks where that run stands so the team can follow it (and retry it).
 */
@Entity
@Table(name = "code_repositories")
@Getter
public class CodeRepository extends AggregateRoot {

    public static final int ERROR_MAX = 500;

    /** A run silent for this long is considered stalled (the server restarted mid-run). */
    public static final Duration STALL_AFTER = Duration.ofMinutes(20);

    /** Where the module lives on the code host, at the indexed commit when there is one. */
    public String treeUrl(String path) {
        String ref = commitSha != null ? commitSha : branch;
        return htmlUrl.replaceAll("/+$", "") + "/tree/" + ref + (path == null || path.isEmpty() ? "" : "/" + path);
    }

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16, updatable = false)
    private CodeHostProvider provider;

    @Column(name = "owner", nullable = false, length = 100, updatable = false)
    private String owner;

    @Column(name = "name", nullable = false, length = 100, updatable = false)
    private String name;

    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    @Column(name = "html_url", nullable = false, length = 500)
    private String htmlUrl;

    @Column(name = "private_repo", nullable = false)
    private boolean privateRepo;

    /** The organization's GitHub App installation that reads it; null for a public repository read anonymously. */
    @Column(name = "installation_id")
    private @Nullable Long installationId;

    /** A commit pushed while a run was in progress: the next run starts as soon as that one ends. */
    @Column(name = "pending_commit", length = 64)
    private @Nullable String pendingCommit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CodeRepositoryStatus status;

    @Column(name = "error", length = ERROR_MAX)
    private @Nullable String error;

    @Column(name = "commit_sha", length = 64)
    private @Nullable String commitSha;

    @Column(name = "indexed_at")
    private @Nullable Instant indexedAt;

    @Column(name = "progress_at")
    private @Nullable Instant progressAt;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "module_count", nullable = false)
    private int moduleCount;

    @Column(name = "modules_done", nullable = false)
    private int modulesDone;

    @Column(name = "summarized", nullable = false)
    private boolean summarized;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile", columnDefinition = "jsonb", nullable = false)
    private CodeProfile profile = CodeProfile.empty();

    protected CodeRepository() {
        super();
    }

    private CodeRepository(UUID projectId, String owner, String name, String branch, String htmlUrl,
                           boolean privateRepo, @Nullable Long installationId, Instant now) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.provider = CodeHostProvider.GITHUB;
        this.owner = Assert.maxLength(Assert.notBlank(owner, "owner"), "owner", 100);
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", 100);
        this.branch = Assert.maxLength(Assert.notBlank(branch, "branch"), "branch", 255);
        this.htmlUrl = Assert.maxLength(Assert.notBlank(htmlUrl, "htmlUrl"), "htmlUrl", 500);
        this.privateRepo = privateRepo;
        this.installationId = installationId;
        this.status = CodeRepositoryStatus.PENDING;
        this.progressAt = now;
    }

    /**
     * Connects a GitHub repository; indexing starts right after. With an installation it is read through the
     * organization's GitHub App (private repositories included) and kept up to date by its push webhooks.
     */
    public static CodeRepository connect(UUID projectId, String owner, String name, String branch, String htmlUrl,
                                         boolean privateRepo, @Nullable Long installationId, Instant now) {
        return new CodeRepository(projectId, owner, name, branch, htmlUrl, privateRepo, installationId, now);
    }

    public String fullName() {
        return owner + "/" + name;
    }

    /** Read through the GitHub App, so every push to its branch updates the index. */
    public boolean viaApp() {
        return installationId != null;
    }

    /** Whether a push to {@code branch} of this repository concerns it (owner and name are case-insensitive). */
    public boolean tracks(String owner, String name, String branch) {
        return this.owner.equalsIgnoreCase(owner) && this.name.equalsIgnoreCase(name) && this.branch.equals(branch);
    }

    /**
     * A push arrived: true when a run should start now. A run in progress keeps the commit for later, and a
     * commit already indexed needs nothing.
     */
    public boolean acceptPush(String commit, Instant now) {
        if (commit == null || commit.isBlank() || commit.equals(commitSha)) return false;
        if (status.isRunning() && !isStalled(now, STALL_AFTER)) {
            this.pendingCommit = commit.length() > 64 ? commit.substring(0, 64) : commit;
            return false;
        }
        requestIndexing(now);
        return true;
    }

    /** The commit pushed during the run that just ended, when it is not the one indexed; it is consumed. */
    public @Nullable String takePendingCommit() {
        String pending = pendingCommit;
        this.pendingCommit = null;
        return pending == null || pending.equals(commitSha) ? null : pending;
    }

    /** GitHub no longer lets ReqsAI read the repository (removed from the App, or the App uninstalled). */
    public void revokeAccess(String reason, Instant now) {
        this.pendingCommit = null;
        markFailed(reason, now);
    }

    /**
     * A run that stopped reporting progress (the server restarted mid-indexing) no longer owns the
     * repository: it reads as failed and can be retried.
     */
    public boolean isStalled(Instant now, Duration after) {
        Instant last = progressAt != null ? progressAt : getUpdatedAt();
        return status.isRunning() && last != null && last.plus(after).isBefore(now);
    }

    /** Queues a new indexing run. */
    public void requestIndexing(Instant now) {
        this.status = CodeRepositoryStatus.PENDING;
        this.error = null;
        this.progressAt = now;
    }

    public void startIndexing(Instant now) {
        this.status = CodeRepositoryStatus.INDEXING;
        this.error = null;
        this.modulesDone = 0;
        this.moduleCount = 0;
        this.progressAt = now;
    }

    /** What the archive holds, known before the modules are summarized. */
    public void recordStructure(String commitSha, int fileCount, int moduleCount, CodeProfile profile, Instant now) {
        this.commitSha = Assert.notBlank(commitSha, "commitSha");
        this.fileCount = Math.max(0, fileCount);
        this.moduleCount = Math.max(0, moduleCount);
        this.profile = Assert.notNull(profile, "profile");
        this.progressAt = now;
    }

    public void recordProgress(int modulesDone, Instant now) {
        this.modulesDone = Math.max(this.modulesDone, Math.min(modulesDone, moduleCount));
        this.progressAt = now;
    }

    public void markReady(boolean summarized, CodeProfile profile, Instant now) {
        this.status = CodeRepositoryStatus.READY;
        this.error = null;
        this.summarized = summarized;
        this.profile = Assert.notNull(profile, "profile");
        this.modulesDone = moduleCount;
        this.indexedAt = now;
        this.progressAt = now;
    }

    public void markFailed(String reason, Instant now) {
        String text = reason == null || reason.isBlank() ? "Indexing failed" : reason.strip();
        this.status = CodeRepositoryStatus.FAILED;
        this.error = text.length() > ERROR_MAX ? text.substring(0, ERROR_MAX - 3) + "..." : text;
        this.progressAt = now;
    }
}
