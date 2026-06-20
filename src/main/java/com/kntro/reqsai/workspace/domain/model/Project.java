package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.domain.event.ProjectConstraintSavedEvent;
import com.kntro.reqsai.workspace.domain.event.ProjectCreatedEvent;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
public class Project extends AggregateRoot {

    private static final int NAME_MAX = 150;
    private static final int DESC_MAX = 2000;

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "description", length = DESC_MAX)
    private @Nullable String description;

    @Embedded
    private TechnicalProfile technicalProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProjectStatus status;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ProjectConstraint> constraints = new ArrayList<>();

    protected Project() {
        super();
    }

    public Project(UUID organizationId, String name, @Nullable String description, TechnicalProfile technicalProfile, UUID createdBy) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
        this.description = description == null ? null : Assert.maxLength(description.strip(), "description", DESC_MAX);
        this.technicalProfile = Assert.notNull(technicalProfile, "technicalProfile");
        this.status = ProjectStatus.ACTIVE;

        registerEvent(ProjectCreatedEvent.of(getId(), organizationId, createdBy));
    }

    public List<ProjectConstraint> getConstraints() {
        return Collections.unmodifiableList(constraints);
    }

    public void updateDetails(String name, @Nullable String description, TechnicalProfile technicalProfile) {
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
        this.description = description == null ? null : Assert.maxLength(description.strip(), "description", DESC_MAX);
        this.technicalProfile = Assert.notNull(technicalProfile, "technicalProfile");
    }

    public ProjectConstraint addConstraint(String description) {
        var c = new ProjectConstraint(this, description);
        constraints.add(c);
        registerEvent(ProjectConstraintSavedEvent.of(getId(), c.getId(), description));
        return c;
    }

    public void updateConstraint(UUID constraintId, String description) {
        findConstraint(constraintId).update(description);
        registerEvent(ProjectConstraintSavedEvent.of(getId(), constraintId, description));
    }

    public void removeConstraint(UUID constraintId) {
        constraints.removeIf(c -> c.getId().equals(constraintId));
    }

    public void applyConstraintEmbedding(UUID constraintId, float[] embedding) {
        findConstraint(constraintId).applyEmbedding(embedding);
    }

    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
    }

    public void activate() {
        this.status = ProjectStatus.ACTIVE;
    }

    private ProjectConstraint findConstraint(UUID constraintId) {
        return constraints.stream()
                .filter(c -> c.getId().equals(constraintId))
                .findFirst()
                .orElseThrow(() -> WorkspaceExceptions.projectConstraintNotFound(constraintId));
    }
}
