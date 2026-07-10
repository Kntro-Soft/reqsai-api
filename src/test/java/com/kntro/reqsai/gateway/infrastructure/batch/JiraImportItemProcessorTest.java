package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.result.ImportStoryResult;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch: import item processor maps service results to item outcomes")
class JiraImportItemProcessorTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final RemoteIssue ISSUE = new RemoteIssue("PAY-1", "Summary", "Story", "desc", "MEDIUM");

    @Mock
    private JiraImportService importService;

    @Test
    @DisplayName("imported -> SUCCEEDED, duplicate -> SKIPPED, failed -> FAILED")
    void maps_outcomes() {
        JiraImportItemProcessor processor = new JiraImportItemProcessor(importService, PROJECT);

        when(importService.importIssue(PROJECT, ISSUE))
                .thenReturn(ImportStoryResult.imported("PAY-1", UUID.randomUUID()))
                .thenReturn(ImportStoryResult.duplicate("PAY-1"))
                .thenReturn(ImportStoryResult.failed("PAY-1", "boom"));

        assertThat(processor.process(ISSUE)).isEqualTo(SyncItemOutcome.SUCCEEDED);
        assertThat(processor.process(ISSUE)).isEqualTo(SyncItemOutcome.SKIPPED);
        assertThat(processor.process(ISSUE)).isEqualTo(SyncItemOutcome.FAILED);
    }
}
