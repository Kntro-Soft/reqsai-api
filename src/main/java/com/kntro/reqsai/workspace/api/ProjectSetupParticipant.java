package com.kntro.reqsai.workspace.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the modules that act on a project's external sources (Gateway for Jira, Codebase for GitHub).
 * Workspace finds every implementation and asks it to check the sources before the project is created, and
 * for the state of its steps afterwards, so Workspace depends on none of them.
 * <p>
 * Runs in the caller's tenant.
 */
public interface ProjectSetupParticipant {

    /**
     * Checks what can be checked right away about the sources this module handles (the Jira connection exists, the
     * repositories are shared by an installation). Throws a domain exception, answered as a 4xx, when they cannot
     * be used; the project is then not created.
     *
     * @param sources     what the project asks for
     * @param requestedBy the user creating the project
     */
    default void validate(ProjectSources sources, UUID requestedBy) {
    }

    /**
     * The steps this module can read from the state it already keeps, for a project that asked for them.
     * A step with nothing to show yet is left out.
     */
    default List<SetupStepView> steps(UUID projectId) {
        return List.of();
    }

    /** The technology found in the project's code, when this module indexes any. */
    default Optional<DetectedStack> detectedStack(UUID projectId) {
        return Optional.empty();
    }
}
