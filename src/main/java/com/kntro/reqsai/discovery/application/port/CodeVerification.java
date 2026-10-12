package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;

import java.util.List;

/**
 * What the deep verification concluded: the verdict, a note (what the code does, or both sides of a conflict) and
 * the places in the code it rests on (file, lines, link to GitHub).
 */
public record CodeVerification(CodeVerdict verdict, String note, List<CodeReference> references) {

    public CodeVerification {
        references = references == null ? List.of() : List.copyOf(references);
    }
}
