package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "glossaries")
@Getter
public class Glossary extends AggregateRoot {

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID projectId;

    @OneToMany(mappedBy = "glossary", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<GlossaryTerm> terms = new ArrayList<>();

    protected Glossary() {
        super();
    }

    public Glossary(UUID projectId) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
    }

    public List<GlossaryTerm> getTerms() {
        return Collections.unmodifiableList(terms);
    }

    public GlossaryTerm addTerm(String term, String definition, UUID addedBy) {
        String normalizedTerm = Assert.notBlank(term, "term");
        boolean exists = terms.stream()
                .anyMatch(existing -> existing.getTerm().trim().equalsIgnoreCase(normalizedTerm));
        if (exists) {
            throw WorkspaceExceptions.glossaryTermAlreadyExists(normalizedTerm);
        }

        GlossaryTerm glossaryTerm = new GlossaryTerm(this, normalizedTerm, definition, addedBy);
        terms.add(glossaryTerm);
        return glossaryTerm;
    }
}
