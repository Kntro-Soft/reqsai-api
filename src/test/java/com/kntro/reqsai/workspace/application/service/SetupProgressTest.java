package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.ProjectSources;
import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.SetupStepView;
import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Application: setup progress merge")
class SetupProgressTest {

    private static final UUID PROJECT = UUID.randomUUID();

    private static ProjectSetupStep row(String key, SetupStepStatus status, String detail) {
        ProjectSetupStep step = new ProjectSetupStep(PROJECT, key, ProjectSources.none());
        if (status != SetupStepStatus.PENDING) step.report(status, detail);
        return step;
    }

    private static SetupStepView live(String key, SetupStepStatus status, int done, int total) {
        return new SetupStepView(key, status, done, total, null);
    }

    @Test
    @DisplayName("lists only the requested steps, in display order")
    void ordersRequestedSteps() {
        var steps = SetupProgress.merge(List.of(
                row(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING, null),
                row(SetupStepKeys.JIRA_LINK, SetupStepStatus.DONE, null)), List.of());

        assertThat(steps).extracting(SetupStepView::key)
                .containsExactly(SetupStepKeys.JIRA_LINK, SetupStepKeys.GITHUB_REPOS);
    }

    @Test
    @DisplayName("shows the live counts of the module that owns a running step")
    void liveCounts() {
        var steps = SetupProgress.merge(
                List.of(row(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING, null)),
                List.of(live(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING, 3, 7)));

        assertThat(steps).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(SetupStepStatus.RUNNING);
            assertThat(step.done()).isEqualTo(3);
            assertThat(step.total()).isEqualTo(7);
        });
    }

    @Test
    @DisplayName("a skip or a recorded failure wins over live data")
    void skipAndFailureWin() {
        var steps = SetupProgress.merge(List.of(
                row(SetupStepKeys.JIRA_IMPORT, SetupStepStatus.SKIPPED, null),
                row(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.FAILED, "acme/x: CODE_REPOSITORY_NOT_FOUND")),
                List.of(live(SetupStepKeys.JIRA_IMPORT, SetupStepStatus.RUNNING, 1, 5),
                        live(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.DONE, 4, 4)));

        assertThat(steps).extracting(SetupStepView::status)
                .containsExactly(SetupStepStatus.SKIPPED, SetupStepStatus.FAILED);
        assertThat(steps.get(1).detail()).contains("CODE_REPOSITORY_NOT_FOUND");
    }

    @Test
    @DisplayName("a step the module has not reported as handled never reads as finished or failed")
    void notFinishedUntilReported() {
        var pendingRow = row(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.PENDING, null);

        assertThat(SetupProgress.merge(List.of(pendingRow),
                List.of(live(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.DONE, 2, 2))))
                .singleElement().extracting(SetupStepView::status).isEqualTo(SetupStepStatus.RUNNING);
        assertThat(SetupProgress.merge(List.of(pendingRow),
                List.of(live(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.FAILED, 0, 0))))
                .singleElement().extracting(SetupStepView::status).isEqualTo(SetupStepStatus.RUNNING);
    }

    @Test
    @DisplayName("keeps the recorded state while the module has nothing to show")
    void recordedStateStands() {
        var steps = SetupProgress.merge(
                List.of(row(SetupStepKeys.JIRA_IMPORT, SetupStepStatus.PENDING, null)), List.of());

        assertThat(steps).singleElement().extracting(SetupStepView::status).isEqualTo(SetupStepStatus.PENDING);
    }

    @Test
    @DisplayName("a late report cannot bring a skipped step back")
    void skippedStaysSkipped() {
        ProjectSetupStep step = row(SetupStepKeys.GITHUB_REPOS, SetupStepStatus.RUNNING, null);
        step.skip();
        step.report(SetupStepStatus.DONE, null);

        assertThat(step.getStatus()).isEqualTo(SetupStepStatus.SKIPPED);
    }
}
