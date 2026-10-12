package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One step of a project's setup (link Jira, import its stories, connect repositories) that the creator asked for.
 * The row records that the step was requested, what was requested (to retry it) and the outcomes the source
 * modules cannot derive from their own state: a failure, a skip. While a step is open, its live progress is read
 * from the source module's own data (the repositories' indexing, the import job).
 */
@Entity
@Table(name = "project_setup_steps")
@Getter
public class ProjectSetupStep extends AggregateRoot {

    public static final int DETAIL_MAX = 500;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "step_key", nullable = false, length = 32, updatable = false)
    private String stepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SetupStepStatus status;

    @Column(name = "detail", length = DETAIL_MAX)
    private @Nullable String detail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request", columnDefinition = "jsonb")
    private @Nullable ProjectSources request;

    protected ProjectSetupStep() {
        super();
    }

    public ProjectSetupStep(UUID projectId, String stepKey, ProjectSources request) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.stepKey = Assert.notBlank(stepKey, "stepKey");
        this.status = SetupStepStatus.PENDING;
        this.request = request;
    }

    /** The source module's outcome. A skipped step stays skipped: a late report must not bring it back. */
    public void report(SetupStepStatus status, @Nullable String detail) {
        if (this.status == SetupStepStatus.SKIPPED) return;
        this.status = Assert.notNull(status, "status");
        this.detail = truncate(detail);
    }

    /** The user stopped waiting for the step; it keeps running if it already started. */
    public void skip() {
        this.status = SetupStepStatus.SKIPPED;
    }

    /** The user asks to try the step again. */
    public void retry() {
        this.status = SetupStepStatus.PENDING;
        this.detail = null;
    }

    private static @Nullable String truncate(@Nullable String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.strip();
        return clean.length() <= DETAIL_MAX ? clean : clean.substring(0, DETAIL_MAX);
    }
}
