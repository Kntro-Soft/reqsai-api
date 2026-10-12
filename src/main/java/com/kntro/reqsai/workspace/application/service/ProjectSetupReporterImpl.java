package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.ProjectSetupReporter;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.application.notification.ProjectSetupNotifier;
import com.kntro.reqsai.workspace.application.port.ProjectSetupStepRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class ProjectSetupReporterImpl implements ProjectSetupReporter {

    private final ProjectSetupStepRepository steps;
    private final ProjectSetupViews views;
    private final ProjectSetupNotifier notifier;

    @Override
    @Transactional
    public void report(UUID projectId, String stepKey, SetupStepStatus status, @Nullable String detail) {
        steps.findByProjectIdAndStepKey(projectId, stepKey).ifPresent(step -> {
            step.report(status, detail);
            steps.save(step);
            notifier.changed(projectId);
        });
    }

    @Override
    public void changed(UUID projectId) {
        if (views.hasSetup(projectId)) notifier.changed(projectId);
    }
}
