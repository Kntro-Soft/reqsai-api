package com.kntro.reqsai.workspace.api;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * How a source module tells Workspace what became of a step it ran for a new project. Workspace keeps the
 * outcome with the project and pushes the project's whole setup to its viewers.
 * <p>
 * Runs in the caller's tenant. A call for a step the project never asked for is ignored.
 */
public interface ProjectSetupReporter {

    /** Records where a step stands, with a short note (the reason of a failure), and pushes the setup. */
    void report(UUID projectId, String stepKey, SetupStepStatus status, @Nullable String detail);

    /**
     * The state a module owns changed (indexing advanced, an import counted an issue): push the project's setup
     * to its viewers. Cheap to call often; pushes are spaced out and skipped for a project with no setup.
     */
    void changed(UUID projectId);
}
