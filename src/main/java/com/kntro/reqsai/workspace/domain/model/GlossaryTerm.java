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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "glossary_terms")
@Getter
public class GlossaryTerm extends AuditableEntity {

    private static final int TERM_MAX = 200;
    private static final int DEFINITION_MAX = 4000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "glossary_id", nullable = false, updatable = false)
    private Glossary glossary;

    @Column(name = "term", nullable = false, length = TERM_MAX)
    private String term;

    @Column(name = "definition", nullable = false, length = DEFINITION_MAX)
    private String definition;

    @Column(name = "added_by", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected GlossaryTerm() {
        super();
    }

    GlossaryTerm(Glossary glossary, String term, String definition, UUID addedBy) {
        super();
        this.glossary = Assert.notNull(glossary, "glossary");
        this.term = Assert.maxLength(Assert.notBlank(term, "term"), "term", TERM_MAX);
        this.definition = Assert.maxLength(Assert.notBlank(definition, "definition"), "definition", DEFINITION_MAX);
        this.addedBy = Assert.notNull(addedBy, "addedBy");
        this.addedAt = Instant.now();
    }
}
