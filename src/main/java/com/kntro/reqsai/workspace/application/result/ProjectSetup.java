package com.kntro.reqsai.workspace.application.result;

import com.kntro.reqsai.workspace.api.DetectedStack;
import com.kntro.reqsai.workspace.api.SetupStepView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Where a project's setup stands: its steps, in display order, each merged from what was requested and what the
 * source modules report, and the technology found in its code once repositories are indexed.
 *
 * @param steps         empty when the project asked for nothing
 * @param detectedStack the stack found in the indexed repositories, or null
 */
public record ProjectSetup(UUID projectId, List<SetupStepView> steps, @Nullable DetectedStack detectedStack) {

    /** True while any step still has work ahead. */
    public boolean active() {
        return steps.stream().anyMatch(step -> step.status().isOpen());
    }
}
