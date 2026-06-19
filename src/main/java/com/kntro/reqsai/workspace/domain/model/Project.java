package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.domain.event.ProjectCreatedEvent;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

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

    public void updateDetails(String name, @Nullable String description, TechnicalProfile technicalProfile) {
        this.name = Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
        this.description = description == null ? null : Assert.maxLength(description.strip(), "description", DESC_MAX);
        this.technicalProfile = Assert.notNull(technicalProfile, "technicalProfile");
    }

    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
    }

    public void activate() {
        this.status = ProjectStatus.ACTIVE;
    }
}
