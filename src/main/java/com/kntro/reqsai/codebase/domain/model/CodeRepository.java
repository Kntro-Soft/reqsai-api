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

    @Column(name = "access_token_ciphertext")
    private byte @Nullable [] accessTokenCiphertext;

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
                           boolean privateRepo, byte @Nullable [] accessTokenCiphertext, Instant now) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.provider = CodeHostProvider.GITHUB;
        this.owner = Assert.maxLength(Assert.notBlank(owner, "owner"), "owner", 100);
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", 100);
        this.branch = Assert.maxLength(Assert.notBlank(branch, "branch"), "branch", 255);
        this.htmlUrl = Assert.maxLength(Assert.notBlank(htmlUrl, "htmlUrl"), "htmlUrl", 500);
        this.privateRepo = privateRepo;
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.status = CodeRepositoryStatus.PENDING;
        this.progressAt = now;
    }

    /** Connects a GitHub repository; indexing starts right after. */
    public static CodeRepository connect(UUID projectId, String owner, String name, String branch, String htmlUrl,
                                         boolean privateRepo, byte @Nullable [] accessTokenCiphertext, Instant now) {
        return new CodeRepository(projectId, owner, name, branch, htmlUrl, privateRepo, accessTokenCiphertext, now);
    }

    public String fullName() {
        return owner + "/" + name;
    }

    public boolean hasToken() {
        return accessTokenCiphertext != null && accessTokenCiphertext.length > 0;
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
