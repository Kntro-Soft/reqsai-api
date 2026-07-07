package com.kntro.reqsai.gateway;

import com.kntro.reqsai.gateway.application.port.JiraOAuthPort;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stubs the Atlassian/Jira HTTP boundary for the OAuth integration test WITHOUT touching the network:
 * <ul>
 *   <li>{@link JiraOAuthPort} — canned code exchange (fixed access/refresh + short expiry) and a single
 *       accessible site {@code cloud-1 / https://acme.atlassian.net}.</li>
 *   <li>{@link JiraClient} — a recording subclass that returns canned verify/project/issue-type/create
 *       results and CAPTURES the {@code apiBase} of every call, so the test can assert an OAUTH2 push
 *       routes to the {@code https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3} base.</li>
 * </ul>
 * The real {@code JiraProvider}, {@code ProviderCredentialsFactory}, {@code JiraOAuthTokenService} and
 * the callback handler run end-to-end so encryption + persistence + dual-mode routing are exercised.
 */
@TestConfiguration
public class StubJiraOAuthConfig {

    /** Records every API base URL the client was called with (for routing assertions). */
    public static final class RecordingJiraClient extends JiraClient {
        public final List<String> apiBases = new CopyOnWriteArrayList<>();

        @Override
        public String verify(JiraApiContext ctx) {
            apiBases.add(ctx.apiBase());
            return "Stub OAuth Admin";
        }

        @Override
        public List<JiraProject> listProjects(JiraApiContext ctx) {
            apiBases.add(ctx.apiBase());
            return List.of(new JiraProject("PAY", "Payments"));
        }

        @Override
        public List<JiraIssueType> listIssueTypes(JiraApiContext ctx, String projectKey) {
            apiBases.add(ctx.apiBase());
            return List.of(new JiraIssueType("10001", "Story"));
        }

        @Override
        public CreatedIssue createIssue(JiraApiContext ctx, String projectKey, String issueTypeName,
                                        String summary, Map<String, Object> descriptionAdf) {
            apiBases.add(ctx.apiBase());
            return new CreatedIssue(projectKey + "-42", ctx.apiBase() + "/issue/" + projectKey + "-42");
        }
    }

    @Bean
    @Primary
    public JiraClient recordingJiraClient() {
        return new RecordingJiraClient();
    }

    @Bean
    @Primary
    public JiraOAuthPort stubJiraOAuthPort() {
        return new JiraOAuthPort() {
            @Override
            public OAuthTokens exchangeCode(String code) {
                // Short-lived access token so a subsequent push exercises the refresh-before-call path too.
                return new OAuthTokens("access-token-1", "refresh-token-1", 3600, "read:jira-work offline_access");
            }

            @Override
            public OAuthTokens refresh(String refreshToken) {
                return new OAuthTokens("access-token-refreshed", "refresh-token-rotated", 3600, "read:jira-work");
            }

            @Override
            public List<Site> accessibleResources(String accessToken) {
                return List.of(new Site("cloud-1", "https://acme.atlassian.net", "Acme"));
            }
        };
    }
}
