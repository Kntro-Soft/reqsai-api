package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.CreatedIssue;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.IssueSearchPage;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraApiContext;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraIssue;
import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraIssueType;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("unit")
@DisplayName("Infrastructure: JiraClient (dual-mode REST, project-scoped types, diagnosable errors, JQL search)")
class JiraClientTest {

    private static final JiraApiContext CTX =
            JiraApiContext.apiToken("https://acme.atlassian.net", "pm@acme.com", "tok");
    private static final String BASE = "https://acme.atlassian.net/rest/api/3";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private JiraClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JiraClient(builder);
    }

    @Test
    @DisplayName("listIssueTypes reads the PROJECT-SCOPED createmeta list and dedupes by id")
    void project_scoped_issue_types_deduped() {
        server.expect(requestTo(BASE + "/issue/createmeta/PAY/issuetypes?maxResults=200"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"issueTypes":[
                          {"id":"10001","name":"Historia"},
                          {"id":"10001","name":"Historia"},
                          {"id":"10002","name":"Tarea"}
                        ]}""", MediaType.APPLICATION_JSON));

        List<JiraIssueType> types = client.listIssueTypes(CTX, "PAY");

        assertThat(types).extracting(JiraIssueType::id).containsExactly("10001", "10002");
        assertThat(types).extracting(JiraIssueType::name).containsExactly("Historia", "Tarea");
        server.verify();
    }

    @Test
    @DisplayName("createIssue resolves the issue type NAME to its project id and sends issuetype:{id}")
    void create_sends_issue_type_by_id() {
        server.expect(requestTo(BASE + "/issue/createmeta/PAY/issuetypes?maxResults=200"))
                .andRespond(withSuccess("{\"issueTypes\":[{\"id\":\"10001\",\"name\":\"Historia\"}]}",
                        MediaType.APPLICATION_JSON));
        // The create-screen meta declares a REQUIRED rich-text custom field (the real-world
        // "Criterios de aceptación" case) — the client must fill it generically or Jira 400s.
        server.expect(requestTo(BASE + "/issue/createmeta/PAY/issuetypes/10001?maxResults=200"))
                .andRespond(withSuccess("{\"fields\":[{\"fieldId\":\"customfield_10037\","
                                + "\"name\":\"Criterios de aceptación\",\"required\":true,"
                                + "\"hasDefaultValue\":false,\"schema\":{\"type\":\"doc\"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/issue"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.fields.issuetype.id").value("10001"))
                .andExpect(jsonPath("$.fields.project.key").value("PAY"))
                .andExpect(jsonPath("$.fields.customfield_10037.type").value("doc"))
                .andExpect(jsonPath("$.fields.customfield_10037.content[0].content[0].text")
                        .value("Given ok, When login, Then home."))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":\"42\",\"key\":\"PAY-42\",\"self\":\"https://acme.atlassian.net/rest/api/3/issue/42\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        CreatedIssue created = client.createIssue(CTX, "PAY", "Historia", "Login con Google",
                Map.of("type", "doc", "version", 1, "content", List.of()),
                "Given ok, When login, Then home.");

        assertThat(created.key()).isEqualTo("PAY-42");
        assertThat(created.id()).isEqualTo("42");
        server.verify();
    }

    @Test
    @DisplayName("createIssue surfaces Jira errorMessages + field errors on a 400 (diagnosable, token-free)")
    void create_surfaces_jira_error_body() {
        server.expect(requestTo(BASE + "/issue/createmeta/PAY/issuetypes?maxResults=200"))
                .andRespond(withSuccess("{\"issueTypes\":[{\"id\":\"10001\",\"name\":\"Historia\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/issue/createmeta/PAY/issuetypes/10001?maxResults=200"))
                .andRespond(withSuccess("{\"fields\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/issue"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"errorMessages\":[\"Field 'customfield_10011' is required\"],"
                                + "\"errors\":{\"summary\":\"Summary must be provided.\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createIssue(CTX, "PAY", "Historia", "x",
                Map.of("type", "doc", "version", 1, "content", List.of()), ""))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("Field 'customfield_10011' is required")
                .hasMessageContaining("summary: Summary must be provided.");
        server.verify();
    }

    @Test
    @DisplayName("resolveIssueTypeId throws a diagnosable error listing available types when the name is invalid")
    void resolve_unknown_issue_type_lists_available() {
        server.expect(requestTo(BASE + "/issue/createmeta/PAY/issuetypes?maxResults=200"))
                .andRespond(withSuccess("{\"issueTypes\":[{\"id\":\"10001\",\"name\":\"Historia\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resolveIssueTypeId(CTX, "PAY", "Bug"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("issue type 'Bug' is not available")
                .hasMessageContaining("Historia");
        server.verify();
    }

    @Test
    @DisplayName("searchAllIssues follows nextPageToken until isLast and concatenates issues")
    void search_paginates_by_token() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/jql?jql=")))
                .andRespond(withSuccess("""
                        {"issues":[{"key":"PAY-1","fields":{"summary":"One"}}],
                         "isLast":false,"nextPageToken":"tok-2"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("nextPageToken=tok-2")))
                .andRespond(withSuccess("""
                        {"issues":[{"key":"PAY-2","fields":{"summary":"Two"}}],
                         "isLast":true}""", MediaType.APPLICATION_JSON));

        List<JiraIssue> issues = client.searchAllIssues(CTX,
                "project = \"PAY\" AND issuetype = \"Historia\" ORDER BY created ASC");

        assertThat(issues).extracting(JiraIssue::key).containsExactly("PAY-1", "PAY-2");
        server.verify();
    }

    @Test
    @DisplayName("a single search page reports isLast and exposes the raw issue fields node")
    void single_search_page() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/jql?jql=")))
                .andRespond(withSuccess("""
                        {"issues":[{"key":"PAY-9","fields":{"summary":"Nine","priority":{"name":"High"}}}],
                         "isLast":true}""", MediaType.APPLICATION_JSON));

        IssueSearchPage page = client.searchIssues(CTX, "project = \"PAY\"", 100, null);

        assertThat(page.isLast()).isTrue();
        assertThat(page.issues()).hasSize(1);
        assertThat(page.issues().getFirst().fields().summary()).isEqualTo("Nine");
        assertThat(page.issues().getFirst().fields().priority().name()).isEqualTo("High");
        server.verify();
    }
}
