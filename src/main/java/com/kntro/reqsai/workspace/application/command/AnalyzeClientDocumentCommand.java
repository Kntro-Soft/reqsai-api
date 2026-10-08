package com.kntro.reqsai.workspace.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Upload a client document (PDF or Word) to a project so its text is extracted and classified by the AI.
 *
 * @param fileName    the original file name sent by the client
 * @param contentType the content type declared by the client
 * @param content     the file bytes
 */
public record AnalyzeClientDocumentCommand(
        UUID organizationId,
        UUID projectId,
        @Nullable String fileName,
        @Nullable String contentType,
        byte[] content,
        UUID requestedBy
) {}
