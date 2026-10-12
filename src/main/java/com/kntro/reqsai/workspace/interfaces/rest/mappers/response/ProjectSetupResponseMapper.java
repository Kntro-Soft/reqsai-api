package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.application.result.ProjectSetup;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectSetupResponse;

public final class ProjectSetupResponseMapper {

    private ProjectSetupResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ProjectSetupResponse toResponse(ProjectSetup setup) {
        var stack = setup.detectedStack();
        return new ProjectSetupResponse(
                setup.projectId(),
                setup.active(),
                setup.steps().stream()
                        .map(step -> new ProjectSetupResponse.Step(step.key(), step.status().name(), step.done(),
                                step.total(), step.detail()))
                        .toList(),
                stack == null ? null : new ProjectSetupResponse.DetectedStack(stack.languages(), stack.frameworks(),
                        stack.databases(), stack.platforms()));
    }
}
