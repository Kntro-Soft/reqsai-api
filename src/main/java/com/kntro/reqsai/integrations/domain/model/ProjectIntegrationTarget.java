package com.kntro.reqsai.integrations.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Project-scoped push target (ADR-0022): the Jira project key + issue type a Reqs-AI project's stories
 * are pushed to, referencing the org-level {@link IntegrationConnection}. Exactly one per project (the
 * {@code PUT .../target} endpoint upserts this single row).
 */
@Entity
@Table(name = "project_integration_targets")
@Getter
public class ProjectIntegrationTarget extends AggregateRoot {

    private static final int KEY_MAX = 100;
    private static final int TYPE_MAX = 100;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "connection_id", columnDefinition = "uuid", nullable = false)
    private UUID connectionId;

    @Column(name = "jira_project_key", nullable = false, length = KEY_MAX)
    private String jiraProjectKey;

    @Column(name = "issue_type_name", nullable = false, length = TYPE_MAX)
    private String issueTypeName;

    protected ProjectIntegrationTarget() {
        super();
    }

    public ProjectIntegrationTarget(UUID projectId, UUID connectionId, String jiraProjectKey, String issueTypeName) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.connectionId = Assert.notNull(connectionId, "connectionId");
        this.jiraProjectKey = normalizeKey(jiraProjectKey);
        this.issueTypeName = normalizeType(issueTypeName);
    }

    public static String normalizeKey(String key) {
        return Assert.maxLength(Assert.notBlank(key, "jiraProjectKey"), "jiraProjectKey", KEY_MAX);
    }

    public static String normalizeType(String type) {
        return Assert.maxLength(Assert.notBlank(type, "issueTypeName"), "issueTypeName", TYPE_MAX);
    }

    /** Re-points this target at a (possibly different) connection, Jira project and issue type. */
    public void update(UUID connectionId, String jiraProjectKey, String issueTypeName) {
        this.connectionId = Assert.notNull(connectionId, "connectionId");
        this.jiraProjectKey = normalizeKey(jiraProjectKey);
        this.issueTypeName = normalizeType(issueTypeName);
    }
}
