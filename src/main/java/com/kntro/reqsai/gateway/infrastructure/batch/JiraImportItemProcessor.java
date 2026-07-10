package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.result.ImportStoryResult;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.util.UUID;

/**
 * Chunk-step processor for the import job: one Jira issue in, one {@link SyncItemOutcome} out,
 * delegating to the existing {@link JiraImportService#importIssue} (LLM mapping + dedup, owned by
 * the application layer — the batch step is only the driver). The service captures per-issue
 * failures itself and reports them as a {@code FAILED} result, so this processor normally never
 * throws; anything unexpected that does escape is handled by the step's skip policy.
 */
@RequiredArgsConstructor
public class JiraImportItemProcessor implements ItemProcessor<RemoteIssue, SyncItemOutcome> {

    private final JiraImportService importService;
    private final UUID projectId;

    @Override
    public SyncItemOutcome process(RemoteIssue issue) {
        ImportStoryResult result = importService.importIssue(projectId, issue);
        return switch (result.status()) {
            case IMPORTED -> SyncItemOutcome.SUCCEEDED;
            case DUPLICATE -> SyncItemOutcome.SKIPPED;
            case FAILED -> SyncItemOutcome.FAILED;
        };
    }
}
