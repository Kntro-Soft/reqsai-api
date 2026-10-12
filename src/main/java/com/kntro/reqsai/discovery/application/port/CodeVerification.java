package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.CodeReference;
import com.kntro.reqsai.discovery.domain.model.CodeVerdict;

import java.util.List;

/**
 * What the deep verification concluded: the verdict, a note (what the code does, or both sides of a conflict) and
 * the places in the code it rests on (file, lines, link to GitHub). {@code tokens} is what the model call
 * consumed (0 when unknown): the caller reports it, on its own thread, to the metering port.
 */
public record CodeVerification(CodeVerdict verdict, String note, List<CodeReference> references, long tokens) {

    public CodeVerification(CodeVerdict verdict, String note, List<CodeReference> references) {
        this(verdict, note, references, 0);
    }

    public CodeVerification withTokens(long consumed) {
        return new CodeVerification(verdict, note, references, consumed);
    }

    public CodeVerification {
        references = references == null ? List.of() : List.copyOf(references);
    }
}
