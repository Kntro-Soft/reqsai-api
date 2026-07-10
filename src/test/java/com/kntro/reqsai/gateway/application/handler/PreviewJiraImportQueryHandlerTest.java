package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.discovery.api.StoryDuplicateCheck;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.query.PreviewJiraImportQuery;
import com.kntro.reqsai.gateway.application.result.ImportPreview;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Preview Jira import query handler")
class PreviewJiraImportQueryHandlerTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private ProjectIntegrationTargetRepository targets;
    @Mock
    private JiraImportService importService;
    @InjectMocks
    private PreviewJiraImportQueryHandler handler;

    @Test
    @DisplayName("flags likely duplicates without importing, and reports the total")
    void flags_duplicates() {
        ProjectIntegrationTarget target = mock(ProjectIntegrationTarget.class);
        when(targets.findByProjectId(PROJECT)).thenReturn(Optional.of(target));
        PushContext ctx = mock(PushContext.class);
        when(importService.contextFor(target)).thenReturn(ctx);
        RemoteIssue a = new RemoteIssue("PAY-1", "Login", "Story", "desc", "HIGH");
        RemoteIssue b = new RemoteIssue("PAY-2", "Logout", "Story", "desc", "LOW");
        when(importService.fetchIssues(ctx)).thenReturn(List.of(a, b));
        UUID existing = UUID.randomUUID();
        when(importService.checkDuplicate(eq(PROJECT), argKey("PAY-1")))
                .thenReturn(new StoryDuplicateCheck(true, existing, 0.9));
        when(importService.checkDuplicate(eq(PROJECT), argKey("PAY-2")))
                .thenReturn(StoryDuplicateCheck.notDuplicate());

        ImportPreview preview = handler.handle(new PreviewJiraImportQuery(PROJECT, USER));

        assertThat(preview.total()).isEqualTo(2);
        assertThat(preview.issues()).hasSize(2);
        ImportPreview.Candidate first = preview.issues().getFirst();
        assertThat(first.jiraIssueKey()).isEqualTo("PAY-1");
        assertThat(first.duplicate()).isTrue();
        assertThat(first.existingStoryId()).isEqualTo(existing);
        assertThat(preview.issues().get(1).duplicate()).isFalse();
    }

    private static RemoteIssue argKey(String key) {
        return org.mockito.ArgumentMatchers.argThat(i -> i != null && key.equals(i.issueKey()));
    }
}
