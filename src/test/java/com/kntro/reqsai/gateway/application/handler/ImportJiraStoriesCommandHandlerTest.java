package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.result.BatchImportResult;
import com.kntro.reqsai.gateway.application.result.ImportStoryResult;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Import Jira stories command handler")
class ImportJiraStoriesCommandHandlerTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ProjectIntegrationTargetRepository targets;
    @Mock
    private JiraImportService importService;
    @InjectMocks
    private ImportJiraStoriesCommandHandler handler;

    @Test
    @DisplayName("imports each eligible issue, counting imported vs duplicate vs failed")
    void imports_counts_outcomes() {
        stubTargetAndIssues(List.of(
                issue("PAY-1"), issue("PAY-2"), issue("PAY-3")));
        UUID storyId = UUID.randomUUID();
        when(importService.importIssue(eq(PROJECT), argKey("PAY-1")))
                .thenReturn(ImportStoryResult.imported("PAY-1", storyId));
        when(importService.importIssue(eq(PROJECT), argKey("PAY-2")))
                .thenReturn(ImportStoryResult.duplicate("PAY-2"));
        when(importService.importIssue(eq(PROJECT), argKey("PAY-3")))
                .thenReturn(ImportStoryResult.failed("PAY-3", "Jira import failed: boom"));

        BatchImportResult result = handler.handle(new ImportJiraStoriesCommand(PROJECT, null, USER));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).hasSize(3);
    }

    @Test
    @DisplayName("issueKeys restricts the import to the requested keys")
    void issue_keys_filter() {
        stubTargetAndIssues(List.of(issue("PAY-1"), issue("PAY-2")));
        when(importService.importIssue(eq(PROJECT), argKey("PAY-2")))
                .thenReturn(ImportStoryResult.imported("PAY-2", UUID.randomUUID()));

        BatchImportResult result = handler.handle(
                new ImportJiraStoriesCommand(PROJECT, List.of("PAY-2"), USER));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.results()).extracting(ImportStoryResult::jiraIssueKey).containsExactly("PAY-2");
        verify(importService, never()).importIssue(eq(PROJECT), argKey("PAY-1"));
    }

    @Test
    @DisplayName("409 INTEGRATION_TARGET_NOT_CONFIGURED when no target exists")
    void no_target_conflicts() {
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ImportJiraStoriesCommand(PROJECT, null, USER)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error().code())
                        .isEqualTo("INTEGRATION_TARGET_NOT_CONFIGURED"));
    }

    private void stubTargetAndIssues(List<RemoteIssue> issues) {
        ProjectIntegrationTarget target = mock(ProjectIntegrationTarget.class);
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.of(target));
        PushContext ctx = mock(PushContext.class);
        when(importService.contextFor(target)).thenReturn(ctx);
        when(importService.fetchIssues(ctx)).thenReturn(issues);
    }

    private static RemoteIssue issue(String key) {
        return new RemoteIssue(key, "Summary " + key, "Story", "desc", "MEDIUM");
    }

    private static RemoteIssue argKey(String key) {
        return org.mockito.ArgumentMatchers.argThat(i -> i != null && key.equals(i.issueKey()));
    }
}
