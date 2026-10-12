package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.SetupStepKeys;
import com.kntro.reqsai.workspace.api.SetupStepStatus;
import com.kntro.reqsai.workspace.api.SetupStepView;
import com.kntro.reqsai.workspace.domain.model.ProjectSetupStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges what Workspace recorded about a project's setup with what the source modules read from their own state.
 * A skip or a failure the user can see wins over live data; otherwise the live view of the owning module is shown,
 * and the recorded state stands while that module has nothing to show yet. A step the module has not yet reported
 * as handled never reads as finished or failed.
 */
final class SetupProgress {

    private SetupProgress() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    static List<SetupStepView> merge(List<ProjectSetupStep> recorded, List<SetupStepView> live) {
        Map<String, ProjectSetupStep> rows = new HashMap<>();
        recorded.forEach(row -> rows.put(row.getStepKey(), row));
        Map<String, SetupStepView> views = new HashMap<>();
        live.forEach(view -> views.putIfAbsent(view.key(), view));

        List<SetupStepView> steps = new ArrayList<>();
        for (String key : SetupStepKeys.ORDER) {
            ProjectSetupStep row = rows.get(key);
            if (row == null) continue;
            steps.add(mergeStep(row, views.get(key)));
        }
        return steps;
    }

    private static SetupStepView mergeStep(ProjectSetupStep row, SetupStepView live) {
        SetupStepStatus status = row.getStatus();
        if (status == SetupStepStatus.SKIPPED || status == SetupStepStatus.FAILED || live == null) {
            return new SetupStepView(row.getStepKey(), status, 0, 0, row.getDetail());
        }
        String detail = live.detail() != null ? live.detail() : row.getDetail();
        // The owning module has not reported that it handled the request yet (it may still be connecting the next
        // repository, or starting the retry of a failed one), so what already persists cannot say "finished" yet.
        boolean settled = live.status() == SetupStepStatus.DONE || live.status() == SetupStepStatus.FAILED;
        SetupStepStatus shown = status == SetupStepStatus.PENDING && settled ? SetupStepStatus.RUNNING : live.status();
        return new SetupStepView(row.getStepKey(), shown, live.done(), live.total(), detail);
    }
}
