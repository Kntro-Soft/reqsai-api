package com.kntro.reqsai.codebase.application.command;

import java.util.UUID;

/** Disconnects a repository and forgets everything indexed from it. */
public record DisconnectRepositoryCommand(UUID projectId, UUID repositoryId) {
}
