package com.kntro.reqsai.codebase.application.result;

import com.kntro.reqsai.codebase.application.port.GitHubAppPort.AppRepository;

/** A repository an installation shares with ReqsAI, and whether the project already reads it. */
public record AvailableRepository(long installationId, AppRepository repository, boolean connected) {
}
