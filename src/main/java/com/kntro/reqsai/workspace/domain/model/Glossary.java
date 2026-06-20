package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.domain.event.GlossaryTermSavedEvent;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

    public GlossaryTerm addTerm(String term, String definition) {
        boolean termExists = terms.stream()
                .anyMatch(t -> t.getTerm().equalsIgnoreCase(term.trim()));
        if (termExists) {
            throw WorkspaceExceptions.glossaryTermAlreadyExists(term);
        }
        var gt = new GlossaryTerm(this, term, definition);
        terms.add(gt);
        registerEvent(GlossaryTermSavedEvent.of(projectId, gt.getId(), term, definition));
        return gt;
    }

    public void updateTerm(UUID termId, String term, String definition) {
        boolean termExists = terms.stream()
                .filter(t -> !t.getId().equals(termId))
                .anyMatch(t -> t.getTerm().equalsIgnoreCase(term.trim()));
        if (termExists) {
            throw WorkspaceExceptions.glossaryTermAlreadyExists(term);
        }
        findTerm(termId).update(term, definition);
        registerEvent(GlossaryTermSavedEvent.of(projectId, termId, term, definition));
    }

    public void removeTerm(UUID termId) {
        terms.removeIf(t -> t.getId().equals(termId));
    }

    public void applyTermEmbedding(UUID termId, float[] embedding) {
        findTerm(termId).applyEmbedding(embedding);
    }

    private GlossaryTerm findTerm(UUID termId) {
        return terms.stream()
                .filter(t -> t.getId().equals(termId))
                .findFirst()
                .orElseThrow(() -> WorkspaceExceptions.glossaryTermNotFound(termId));
    }
}
