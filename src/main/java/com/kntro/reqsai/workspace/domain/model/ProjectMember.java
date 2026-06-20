package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
public class ProjectMember extends AggregateRoot {

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "member_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "role_id", columnDefinition = "uuid", nullable = false)
    private UUID roleId;

    @Column(name = "assigned_by", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected ProjectMember() {
        super();
    }

    public ProjectMember(UUID projectId, UUID memberId, UUID roleId, UUID assignedBy, Instant assignedAt) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.memberId = Assert.notNull(memberId, "memberId");
        this.roleId = Assert.notNull(roleId, "roleId");
        this.assignedBy = Assert.notNull(assignedBy, "assignedBy");
        this.assignedAt = Assert.notNull(assignedAt, "assignedAt");
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void changeRole(UUID roleId) {
        this.roleId = Assert.notNull(roleId, "roleId");
    }
}
