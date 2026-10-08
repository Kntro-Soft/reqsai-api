package com.kntro.reqsai.workspace.application.port;

import org.jspecify.annotations.Nullable;

/**
 * What the classifier reads: the document text (already capped for the model) and the project it
 * belongs to, so terms and constraints are judged against that domain.
 *
 * @param fileName      the uploaded file name
 * @param projectName   the project name
 * @param projectDomain the project's business domain, when set
 * @param text          the document text sent to the model
 * @param truncated     whether {@code text} is only the beginning of the document
 */
public record DocumentClassificationRequest(
        String fileName,
        String projectName,
        @Nullable String projectDomain,
        String text,
        boolean truncated
) {}
