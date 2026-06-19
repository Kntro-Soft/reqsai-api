package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import java.util.UUID;

@Entity
@Table(name = "glossaries")
@Getter
public class Glossary extends AggregateRoot {

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID projectId;

    protected Glossary() {
        super();
    }

    public Glossary(UUID projectId) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
    }
}
