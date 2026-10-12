package com.kntro.reqsai.codebase.application.result;

import com.kntro.reqsai.codebase.domain.model.CodeHostInstallation;
import org.jspecify.annotations.Nullable;

/**
 * How an install ended: {@code LINKED} to the organization, or {@code REQUESTED} when a GitHub organization
 * member asked an owner to approve it (GitHub tells nothing more until then).
 */
public record GitHubInstallResult(Status status, @Nullable CodeHostInstallation installation) {

    public enum Status { LINKED, REQUESTED }
}
