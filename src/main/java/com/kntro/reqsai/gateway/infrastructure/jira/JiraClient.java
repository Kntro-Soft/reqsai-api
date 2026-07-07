package com.kntro.reqsai.gateway.infrastructure.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Outbound Jira Cloud REST v3 client (ADR-0022), dual-mode across the two credential types:
 * <ul>
 *   <li><strong>API_TOKEN</strong> — base {@code https://{site}/rest/api/3} with basic auth
 *       ({@code Authorization: Basic base64(email:token)}).</li>
 *   <li><strong>OAUTH2</strong> — base {@code https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3}
 *       with bearer auth ({@code Authorization: Bearer {access}}).</li>
 * </ul>
 * The base URL + {@code Authorization} header are supplied per call via a {@link JiraApiContext} built by
 * {@link JiraProvider}, so the same call code serves both modes. Neither the token nor the header is ever
 * logged or placed in exceptions.
 * <ul>
 *   <li>401/403 → {@code JIRA_AUTH_FAILED}</li>
 *   <li>connect/timeout/5xx → {@code JIRA_UNREACHABLE}</li>
 *   <li>400 on create → {@code JIRA_PUSH_FAILED}</li>
 * </ul>
 */
@Component
@Slf4j
public class JiraClient {

    private static final String OAUTH_API_BASE = "https://api.atlassian.com/ex/jira/";

    private final RestClient restClient = RestClient.create();

    /**
     * The per-call base URL + {@code Authorization} header for a Jira REST v3 call. The {@code browseBase}
     * is the human site URL used to build a {@code /browse/{key}} link (same for both modes).
     */
    public record JiraApiContext(String apiBase, String authHeader, String browseBase) {

        /** API-token context: {@code https://{site}/rest/api/3} + basic auth. */
        public static JiraApiContext apiToken(String siteUrl, String email, String token) {
            return new JiraApiContext(siteUrl + "/rest/api/3", basic(email, token), siteUrl);
        }

        /** OAuth context: {@code https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3} + bearer auth. */
        public static JiraApiContext oauth(String cloudId, String accessToken, String browseBase) {
            return new JiraApiContext(OAUTH_API_BASE + cloudId + "/rest/api/3", "Bearer " + accessToken, browseBase);
        }

        private static String basic(String email, String token) {
            String raw = email + ":" + token;
            return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** GET /myself → the authenticated account's display name. */
    public String verify(JiraApiContext ctx) {
        Myself me = exchange(() -> restClient.get()
                .uri(ctx.apiBase() + "/myself")
                .header("Authorization", ctx.authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> mapError(res.getStatusCode(), false))
                .body(Myself.class), "verify");
        return me != null ? me.displayName() : "";
    }

    /** GET /project/search → visible projects. */
    public List<JiraProject> listProjects(JiraApiContext ctx) {
        ProjectSearch search = exchange(() -> restClient.get()
                .uri(ctx.apiBase() + "/project/search?maxResults=100")
                .header("Authorization", ctx.authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> mapError(res.getStatusCode(), false))
                .body(ProjectSearch.class), "listProjects");
        return search == null || search.values() == null ? List.of() : search.values();
    }

    /** GET /issuetype → the global issue-type list (project param is accepted for parity, unused). */
    public List<JiraIssueType> listIssueTypes(JiraApiContext ctx, String projectKey) {
        List<JiraIssueType> types = exchange(() -> restClient.get()
                .uri(ctx.apiBase() + "/issuetype")
                .header("Authorization", ctx.authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> mapError(res.getStatusCode(), false))
                .body(ISSUE_TYPE_LIST), "listIssueTypes");
        return types == null ? List.of() : types;
    }

    /** POST /issue → the created issue's key + self URL. */
    public CreatedIssue createIssue(JiraApiContext ctx, String projectKey, String issueTypeName,
                                    String summary, Map<String, Object> descriptionAdf) {
        Map<String, Object> fields = Map.of(
                "project", Map.of("key", projectKey),
                "issuetype", Map.of("name", issueTypeName),
                "summary", summary,
                "description", descriptionAdf);
        CreatedIssue created = exchange(() -> restClient.post()
                .uri(ctx.apiBase() + "/issue")
                .header("Authorization", ctx.authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("fields", fields))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> mapError(res.getStatusCode(), true))
                .body(CreatedIssue.class), "createIssue");
        if (created == null || created.key() == null) {
            throw IntegrationsInfrastructureExceptions.jiraPushFailed("Jira returned no issue key");
        }
        return created;
    }

    /** Browse URL for a created issue (uses the human site URL, not the OAuth API base). */
    public String browseUrl(String browseBase, String issueKey) {
        return browseBase + "/browse/" + issueKey;
    }

    // Helpers

    /**
     * Maps an error status inside the RestClient exchange. {@code onCreate} selects the 400 → push-failed
     * mapping; otherwise 400 falls through to unreachable. Throwing here aborts the call with a mapped,
     * token-free exception.
     */
    private static RuntimeException mapError(HttpStatusCode status, boolean onCreate) {
        if (status.value() == 401 || status.value() == 403) {
            return IntegrationsInfrastructureExceptions.jiraAuthFailed();
        }
        if (onCreate && status.value() == 400) {
            return IntegrationsInfrastructureExceptions.jiraPushFailed("Jira rejected the request (400)");
        }
        return IntegrationsInfrastructureExceptions.jiraUnreachable("HTTP " + status.value(), null);
    }

    /** Runs a RestClient call, translating transport-level failures (connect/timeout) to JIRA_UNREACHABLE. */
    private <T> T exchange(java.util.function.Supplier<T> call, String op) {
        try {
            return call.get();
        } catch (com.kntro.reqsai.shared.domain.exception.InfrastructureException mapped) {
            throw mapped; // already mapped by onStatus
        } catch (Exception e) {
            log.warn("Jira {} failed: {}", op, e.getMessage());
            throw IntegrationsInfrastructureExceptions.jiraUnreachable(op, e);
        }
    }

    private static final org.springframework.core.ParameterizedTypeReference<List<JiraIssueType>> ISSUE_TYPE_LIST =
            new org.springframework.core.ParameterizedTypeReference<>() {};

    // Jackson-bound response records

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Myself(String displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraProject(String key, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProjectSearch(List<JiraProject> values) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraIssueType(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatedIssue(String key, String self) {}
}
