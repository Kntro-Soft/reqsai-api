package com.kntro.reqsai.workspace.application.notification;

import java.util.Objects;
import java.util.UUID;

/**
 * The logical topic of a project's setup. The shared notifier prepends {@code /topic}, so subscribers listen on
 * {@code /topic/projects/{projectId}/setup}.
 */
public final class ProjectSetupTopics {

    private ProjectSetupTopics() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static String of(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return "projects/" + projectId + "/setup";
    }
}
