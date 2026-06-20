package com.kntro.reqsai.workspace.mothers;

import com.kntro.reqsai.workspace.domain.model.Glossary;

import java.util.UUID;

public class GlossaryBuilder {

    private UUID projectId = UUID.randomUUID();

    public static GlossaryBuilder aGlossary() {
        return new GlossaryBuilder();
    }

    public GlossaryBuilder withProjectId(UUID projectId) {
        this.projectId = projectId;
        return this;
    }

    public Glossary build() {
        return new Glossary(projectId);
    }
}
