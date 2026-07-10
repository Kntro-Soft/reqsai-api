package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider.ProviderCredentials;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.IssueFields;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraApiContext;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraIssue;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.NamedRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Infrastructure: Jira issue -> RemoteIssue import mapping (priority + ADF flatten)")
class JiraProviderImportMappingTest {

    private static final ProviderCredentials CREDS =
            ProviderCredentials.apiToken("https://acme.atlassian.net", "pm@acme.com", "tok");

    @Test
    @DisplayName("maps summary, ADF description (flattened), issue type and priority scale")
    void maps_issue_fields() {
        Map<String, Object> adf = Map.of(
                "type", "doc", "version", 1,
                "content", List.of(
                        Map.of("type", "paragraph", "content",
                                List.of(Map.of("type", "text", "text", "As a user I want to reset my password.")))));
        JiraIssue high = new JiraIssue("PAY-1",
                new IssueFields("Password reset", adf, new NamedRef("10001", "Historia"), new NamedRef("2", "High")));
        JiraIssue lowest = new JiraIssue("PAY-2",
                new IssueFields("Tidy up", null, new NamedRef("10002", "Tarea"), new NamedRef("5", "Lowest")));
        JiraIssue noPriority = new JiraIssue("PAY-3",
                new IssueFields("No priority", null, new NamedRef("10001", "Historia"), null));

        JiraProvider provider = new JiraProvider(new StubSearchClient(List.of(high, lowest, noPriority)));
        List<RemoteIssue> issues = provider.searchImportableIssues(CREDS, "PAY", "Historia");

        assertThat(issues).hasSize(3);
        assertThat(issues.get(0).issueKey()).isEqualTo("PAY-1");
        assertThat(issues.get(0).summary()).isEqualTo("Password reset");
        assertThat(issues.get(0).description()).contains("reset my password");
        assertThat(issues.get(0).issueType()).isEqualTo("Historia");
        assertThat(issues.get(0).priority()).isEqualTo("HIGH");
        assertThat(issues.get(1).priority()).isEqualTo("LOW");   // Lowest -> LOW
        assertThat(issues.get(1).description()).isEmpty();         // null ADF -> ""
        assertThat(issues.get(2).priority()).isEqualTo("MEDIUM"); // no priority -> MEDIUM
    }

    /** Minimal JiraClient stand-in that returns a fixed search result. */
    private static final class StubSearchClient extends JiraClient {
        private final List<JiraIssue> result;

        private StubSearchClient(List<JiraIssue> result) {
            this.result = result;
        }

        @Override
        public List<JiraIssue> searchAllIssues(JiraApiContext ctx, String jql) {
            return result;
        }
    }
}
