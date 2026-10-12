package com.kntro.reqsai.codebase.application.command;

import java.util.UUID;

/** Reads the repository again at its branch's head; only modules whose files changed are summarized again. */
public record ReindexRepositoryCommand(UUID projectId, UUID repositoryId) {
}
