package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.integrations.application.command.PushStoryCommand;
import com.kntro.reqsai.integrations.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider.PushedIssue;
import com.kntro.reqsai.integrations.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.integrations.application.result.StoryPushResult;
import com.kntro.reqsai.integrations.application.service.ProviderCredentialsFactory;
import com.kntro.reqsai.integrations.application.service.ProviderRegistry;
import com.kntro.reqsai.integrations.application.service.StoryPushService;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import com.kntro.reqsai.integrations.domain.model.IntegrationProviderType;
import com.kntro.reqsai.integrations.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.shared.domain.exception.DomainException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Application: Push single story")
@ExtendWith(MockitoExtension.class)
class PushStoryCommandHandlerTest {

    @Mock private ProjectIntegrationTargetRepository targets;
    @Mock private DiscoveryStoryReadPort stories;
    @Mock private IntegrationConnectionRepository connections;
    @Mock private IntegrationProvider jiraProvider;
    @Mock private ProviderCredentialsFactory credentialsFactory;

    private PushStoryCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(jiraProvider.type()).thenReturn(IntegrationProviderType.JIRA);
        StoryPushService pushService = new StoryPushService(
                connections, new ProviderRegistry(List.of(jiraProvider)), credentialsFactory);
        handler = new PushStoryCommandHandler(targets, stories, pushService);
    }

    @Test
    @DisplayName("pushes the story and returns the issue key + url")
    void pushes_story() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ProjectIntegrationTarget target = new ProjectIntegrationTarget(projectId, connectionId, "PAY", "Story");
        IntegrationConnection connection = new IntegrationConnection(
                UUID.randomUUID(), IntegrationProviderType.JIRA, "https://acme.atlassian.net",
                "pm@acme.com", "tok", Instant.now());
        StoryView story = new StoryView(storyId, projectId, "T", "user", "do", "benefit", "HIGH", 2, List.of());

        when(targets.findByProjectId(projectId)).thenReturn(Optional.of(target));
        when(stories.findStory(projectId, storyId)).thenReturn(Optional.of(story));
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(credentialsFactory.from(connection)).thenReturn(
                new IntegrationProvider.ProviderCredentials("https://acme.atlassian.net", "pm@acme.com", "tok"));
        when(jiraProvider.pushStory(any(), any(), any(), any()))
                .thenReturn(new PushedIssue("PAY-7", "https://acme.atlassian.net/browse/PAY-7"));

        StoryPushResult result = handler.handle(new PushStoryCommand(projectId, storyId, UUID.randomUUID()));

        assertThat(result.jiraIssueKey()).isEqualTo("PAY-7");
        assertThat(result.jiraIssueUrl()).isEqualTo("https://acme.atlassian.net/browse/PAY-7");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("returns 409 when no target is configured")
    void no_target_configured() {
        UUID projectId = UUID.randomUUID();
        when(targets.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new PushStoryCommand(projectId, UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error().code())
                .isEqualTo("INTEGRATION_TARGET_NOT_CONFIGURED");
    }
}
