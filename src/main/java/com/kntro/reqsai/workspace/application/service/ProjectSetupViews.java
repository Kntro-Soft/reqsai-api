package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.DetectedStack;
import com.kntro.reqsai.workspace.api.ProjectSetupParticipant;
import com.kntro.reqsai.workspace.api.SetupStepView;
import com.kntro.reqsai.workspace.application.port.ProjectSetupStepRepository;
import com.kntro.reqsai.workspace.application.result.ProjectSetup;
import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the current setup of a project from the steps it asked for and the state its source modules keep. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectSetupViews {

    private final ProjectSetupStepRepository steps;
    private final ObjectProvider<ProjectSetupParticipant> participants;

    @Transactional(readOnly = true)
    public ProjectSetup of(UUID projectId) {
        List<ProjectSetupStep> recorded = steps.findAllByProjectId(projectId);
        if (recorded.isEmpty()) {
            return new ProjectSetup(projectId, List.of(), null);
        }
        List<SetupStepView> live = new ArrayList<>();
        DetectedStack stack = null;
        for (ProjectSetupParticipant participant : participants) {
            try {
                live.addAll(participant.steps(projectId));
                if (stack == null) {
                    stack = participant.detectedStack(projectId).filter(found -> !found.isEmpty()).orElse(null);
                }
            } catch (RuntimeException e) {
                log.warn("Setup state of project {} unavailable from {}: {}", projectId,
                        participant.getClass().getSimpleName(), e.getMessage());
            }
        }
        return new ProjectSetup(projectId, SetupProgress.merge(recorded, live), stack);
    }

    @Transactional(readOnly = true)
    public boolean hasSetup(UUID projectId) {
        return !steps.findAllByProjectId(projectId).isEmpty();
    }
}
