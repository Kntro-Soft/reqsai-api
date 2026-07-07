package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A non-functional or business constraint that applies to the whole project
 * (e.g. "Must comply with PCI-DSS", "Max response time 200 ms for payment flows").
 * The {@code embedding} enables semantic retrieval so the LLM receives only the
 * most relevant constraints for a given transcript segment.
 * Non-root entity: no repository, always loaded/saved through {@link Project}.
 */
@Entity
@Table(name = "project_constraints")
@Getter
public class ProjectConstraint extends AuditableEntity {

    private static final int DESCRIPTION_MAX = 1000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingPort.DIMENSIONS)
    @Column(name = "embedding")
    private float @Nullable [] embedding;

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
        this.embedding = null;
    }

    void applyEmbedding(float[] embedding) {
        this.embedding = Assert.notNull(embedding, "embedding");
    }
}
