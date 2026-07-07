package com.kntro.reqsai.gateway;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider;
import com.kntro.reqsai.gateway.domain.model.IntegrationProviderType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Deterministic stand-in for {@code JiraProvider} used by integration tests: it stubs the RestClient
 * boundary so the tests exercise the full connect → target → push flow against a real tenant schema
 * WITHOUT hitting Jira. Verification always succeeds; a push returns a synthetic issue key/url derived
 * from the story id so assertions are stable.
 */
@TestConfiguration
public class StubJiraProviderConfig {

    public static final String ACCOUNT_NAME = "Stub Jira Admin";

    @Bean
    @Primary
    public IntegrationProvider stubJiraProvider() {
        return new IntegrationProvider() {
            @Override
            public IntegrationProviderType type() {
                return IntegrationProviderType.JIRA;
            }

            @Override
            public String verify(ProviderCredentials credentials) {
                return ACCOUNT_NAME;
            }

            @Override
            public List<RemoteProject> listProjects(ProviderCredentials credentials) {
                return List.of(new RemoteProject("PAY", "Payments"));
            }

            @Override
            public List<RemoteIssueType> listIssueTypes(ProviderCredentials credentials, String projectKey) {
                return List.of(new RemoteIssueType("10001", "Story"));
            }

            @Override
            public PushedIssue pushStory(ProviderCredentials c, String projectKey, String issueTypeName, StoryView story) {
                String key = projectKey + "-" + Math.abs(story.storyId().hashCode() % 1000);
                return new PushedIssue(key, c.siteUrl() + "/browse/" + key);
            }
        };
    }
}
