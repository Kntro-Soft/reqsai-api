package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.Glossary;

public interface GlossaryRepository {
    Glossary save(Glossary glossary);
}
