package com.kntro.reqsai.codebase.application.result;

import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;

import java.util.List;

/**
 * The organization's GitHub connection.
 *
 * @param available whether this server has the GitHub App configured
 */
public record GitHubConnection(boolean available, List<CodeHostInstallation> installations) {
}
