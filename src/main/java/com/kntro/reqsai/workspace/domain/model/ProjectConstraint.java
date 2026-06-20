package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "project_constraints")
@Getter
public class ProjectConstraint extends AuditableEntity {

    static final int DESCRIPTION_MAX = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX)
    private String description;

    protected ProjectConstraint() {
        super();
    }

    ProjectConstraint(Project project, String description) {
        super();
        this.project = Assert.notNull(project, "project");
        this.description = normalizeDescription(description);
    }

    static String normalizeDescription(String description) {
        return Assert.maxLength(Assert.notBlank(description, "description"), "description", DESCRIPTION_MAX);
    }

    boolean sameDescription(String description) {
        return this.description.equalsIgnoreCase(normalizeDescription(description));
    }

    void update(String description) {
        this.description = normalizeDescription(description);
    }
}
