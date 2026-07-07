package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.command.PushAllStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.PushedIssue;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.result.BatchPushResult;
import com.kntro.reqsai.gateway.application.service.ProviderCredentialsFactory;
import com.kntro.reqsai.gateway.application.service.ProviderRegistry;
import com.kntro.reqsai.gateway.application.service.StoryPushService;
import com.kntro.reqsai.gateway.domain.model.ConnectionStatus;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Application: Push all stories (partial failure)")
@ExtendWith(MockitoExtension.class)
class PushAllStoriesCommandHandlerTest {

    @Mock private ProjectIntegrationTargetRepository targets;
    @Mock private DiscoveryStoryReadPort stories;
    @Mock private IntegrationConnectionRepository connections;
    @Mock private IntegrationProvider jiraProvider;
    @Mock private ProviderCredentialsFactory credentialsFactory;

    private PushAllStoriesCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(jiraProvider.type()).thenReturn(IntegrationProviderType.JIRA);
        StoryPushService pushService = new StoryPushService(
                connections, new ProviderRegistry(List.of(jiraProvider)), credentialsFactory);
        handler = new PushAllStoriesCommandHandler(targets, stories, pushService);
    }

    @Test
    @DisplayName("captures a per-story failure without aborting the batch")
    void captures_partial_failure() {
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ProjectIntegrationTarget target = new ProjectIntegrationTarget(projectId, connectionId, "PAY", "Story");
        IntegrationConnection connection = new IntegrationConnection(
                UUID.randomUUID(), IntegrationProviderType.JIRA, "https://acme.atlassian.net",
                "pm@acme.com", "tok", Instant.now());

        when(targets.findByProjectId(projectId)).thenReturn(Optional.of(target));
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(credentialsFactory.from(connection)).thenReturn(
                new IntegrationProvider.ProviderCredentials("https://acme.atlassian.net", "pm@acme.com", "tok"));

        StoryView ok = story(projectId, "Good story");
        StoryView bad = story(projectId, "Bad story");
        when(stories.listStories(projectId)).thenReturn(List.of(ok, bad));

        when(jiraProvider.pushStory(any(), eq("PAY"), eq("Story"), eq(ok)))
                .thenReturn(new PushedIssue("PAY-1", "https://acme.atlassian.net/browse/PAY-1"));
        when(jiraProvider.pushStory(any(), eq("PAY"), eq("Story"), eq(bad)))
                .thenThrow(IntegrationsInfrastructureExceptions.jiraPushFailed("400"));

        BatchPushResult result = handler.handle(new PushAllStoriesCommand(projectId, UUID.randomUUID()));

        assertThat(result.pushed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(0).jiraIssueKey()).isEqualTo("PAY-1");
        assertThat(result.results().get(1).error()).isEqualTo("JIRA_PUSH_FAILED");
        assertThat(result.results().get(1).jiraIssueKey()).isNull();
    }

    private static StoryView story(UUID projectId, String title) {
        return new StoryView(UUID.randomUUID(), projectId, title, "user", "do", "benefit", "MEDIUM", null, List.of());
    }
}
