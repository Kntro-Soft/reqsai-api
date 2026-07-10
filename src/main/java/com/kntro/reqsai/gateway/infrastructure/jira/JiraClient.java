package com.kntro.reqsai.gateway.infrastructure.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.gateway.infrastructure.exception.IntegrationsInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbound Jira Cloud REST v3 client (ADR-0023), dual-mode across the two credential types:
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
 *   <li>400 on create → {@code JIRA_PUSH_FAILED} (with Jira's {@code errorMessages}/field {@code errors})</li>
 * </ul>
 *
 * <p>Issue types are resolved <strong>project-scoped</strong> via {@code createmeta/{projectKey}/issuetypes}
 * (the new endpoint; the legacy global {@code /issuetype} returns duplicate names across projects and its
 * ids are not always accepted by team-managed projects). Issue creation always sends the issue type by
 * <strong>id</strong> (resolved for the specific project), which team-managed / localized projects
 * (e.g. Spanish "Historia") require.
 */
@Component
@Slf4j
public class JiraClient {

    private static final String OAUTH_API_BASE = "https://api.atlassian.com/ex/jira/";
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public JiraClient() {
        this(RestClient.builder());
    }

    /** Builder-based constructor so tests can bind a {@code MockRestServiceServer} at the HTTP boundary. */
    public JiraClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

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
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw mapError(res, false); })
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
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw mapError(res, false); })
                .body(ProjectSearch.class), "listProjects");
        return search == null || search.values() == null ? List.of() : search.values();
    }

    /**
     * GET {@code /issue/createmeta/{projectKey}/issuetypes} → the issue types valid for that specific
     * project, each with its project-scoped id (deduped by id). Fixes the previous behaviour of returning
     * the GLOBAL {@code /issuetype} list, which repeated names ("Historia", "Tarea", …) across projects
     * and produced ids that team-managed projects reject on create.
     */
    public List<JiraIssueType> listIssueTypes(JiraApiContext ctx, String projectKey) {
        CreateMetaIssueTypes meta = exchange(() -> restClient.get()
                .uri(ctx.apiBase() + "/issue/createmeta/" + enc(projectKey) + "/issuetypes?maxResults=200")
                .header("Authorization", ctx.authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw mapError(res, false); })
                .body(CreateMetaIssueTypes.class), "listIssueTypes");
        if (meta == null || meta.issueTypes() == null) {
            return List.of();
        }
        Map<String, JiraIssueType> byId = new LinkedHashMap<>();
        for (JiraIssueType t : meta.issueTypes()) {
            if (t.id() != null) {
                byId.putIfAbsent(t.id(), t);
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * Resolves the issue type <strong>id</strong> for {@code issueTypeName} within {@code projectKey}. The
     * mapped target stores a human name ("Story" / "Historia"); create requires the project-scoped id.
     * Matches by exact name (case-insensitive). Throws {@code JIRA_PUSH_FAILED} listing the available types
     * when the name is not valid for the project.
     */
    public String resolveIssueTypeId(JiraApiContext ctx, String projectKey, String issueTypeName) {
        List<JiraIssueType> types = listIssueTypes(ctx, projectKey);
        return types.stream()
                .filter(t -> t.name() != null && t.name().equalsIgnoreCase(issueTypeName))
                .map(JiraIssueType::id)
                .findFirst()
                .orElseThrow(() -> IntegrationsInfrastructureExceptions.jiraPushFailed(
                        "issue type '" + issueTypeName + "' is not available in project '" + projectKey
                                + "' (available: " + types.stream().map(JiraIssueType::name).toList() + ")"));
    }

    /**
     * GET {@code /issue/createmeta/{projectKey}/issuetypes/{issueTypeId}} → the create-screen fields for
     * that project + issue type (required flag, default flag and schema type). Used to satisfy
     * project-specific REQUIRED custom fields (e.g. a mandatory "Criterios de aceptación" text field) that
     * would otherwise fail the create with a 400.
     */
    public List<CreateField> listCreateFields(JiraApiContext ctx, String projectKey, String issueTypeId) {
        CreateMetaFields meta = exchange(() -> restClient.get()
                .uri(ctx.apiBase() + "/issue/createmeta/" + enc(projectKey) + "/issuetypes/"
                        + enc(issueTypeId) + "?maxResults=200")
                .header("Authorization", ctx.authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw mapError(res, false); })
                .body(CreateMetaFields.class), "listCreateFields");
        return meta == null || meta.fields() == null ? List.of() : meta.fields();
    }

    /** The base create fields Reqs-AI always sends; anything else required must be filled generically. */
    private static final java.util.Set<String> BASE_FIELDS =
            java.util.Set.of("project", "issuetype", "summary", "description", "reporter");

    /**
     * POST /issue → the created issue's id/key/self. Sends {@code issuetype:{id:…}} (resolved for the
     * project) so team-managed and localized projects accept the create. Any OTHER field the project marks
     * required without a default (custom fields like "Criterios de aceptación") is filled generically from
     * {@code requiredFieldFallbackText}: plain text for {@code string} fields, an ADF doc for {@code doc}
     * fields (unfillable types are left for Jira to report). Reads Jira's error body on failure and
     * surfaces its {@code errorMessages}/field {@code errors} (token-free) for diagnosability.
     */
    public CreatedIssue createIssue(JiraApiContext ctx, String projectKey, String issueTypeName,
                                    String summary, Map<String, Object> descriptionAdf,
                                    String requiredFieldFallbackText) {
        String issueTypeId = resolveIssueTypeId(ctx, projectKey, issueTypeName);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("issuetype", Map.of("id", issueTypeId));
        fields.put("summary", summary);
        fields.put("description", descriptionAdf);
        fillRequiredCustomFields(ctx, projectKey, issueTypeId, fields, requiredFieldFallbackText);
        CreatedIssue created = exchange(() -> restClient.post()
                .uri(ctx.apiBase() + "/issue")
                .header("Authorization", ctx.authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("fields", fields))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw mapError(res, true); })
                .body(CreatedIssue.class), "createIssue");
        if (created == null || created.key() == null) {
            throw IntegrationsInfrastructureExceptions.jiraPushFailed(
                    "Jira accepted the request but returned no issue key (project '" + projectKey
                            + "', issue type id '" + issueTypeId + "')");
        }
        return created;
    }

    /**
     * GET {@code /search/jql} → one page of issues matching {@code jql}, requesting only the
     * {@code summary}, {@code description}, {@code issuetype} and {@code priority} fields. Token-paginated
     * (the current Jira Cloud model: {@code nextPageToken} + {@code isLast}, no {@code total}). Returns the
     * raw {@code issues} nodes plus the next-page token; the caller loops until {@code isLast}.
     */
    public IssueSearchPage searchIssues(JiraApiContext ctx, String jql, int maxResults, String nextPageToken) {
        StringBuilder uri = new StringBuilder(ctx.apiBase())
                .append("/search/jql?jql=").append(enc(jql))
                .append("&fields=").append(enc("summary,description,issuetype,priority"))
                .append("&maxResults=").append(maxResults);
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            uri.append("&nextPageToken=").append(enc(nextPageToken));
        }
        // The JQL is already URL-encoded via enc(); pass a java.net.URI so RestClient does NOT treat the
        // string as a template and re-encode it (double-encoding turned %22 into %2522 → Jira 400).
        IssueSearchResponse res = exchange(() -> restClient.get()
                .uri(java.net.URI.create(uri.toString()))
                .header("Authorization", ctx.authHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, r) -> { throw mapError(r, false); })
                .body(IssueSearchResponse.class), "searchIssues");
        if (res == null) {
            return new IssueSearchPage(List.of(), true, null);
        }
        List<JiraIssue> issues = res.issues() == null ? List.of() : res.issues();
        return new IssueSearchPage(issues, res.isLast() == null || res.isLast(), res.nextPageToken());
    }

    /** Fetches every issue matching {@code jql} across all pages (created ASC ordering is the caller's). */
    public List<JiraIssue> searchAllIssues(JiraApiContext ctx, String jql) {
        List<JiraIssue> all = new ArrayList<>();
        String token = null;
        do {
            IssueSearchPage page = searchIssues(ctx, jql, 100, token);
            all.addAll(page.issues());
            token = page.isLast() ? null : page.nextPageToken();
        } while (token != null);
        return all;
    }

    /** Browse URL for a created issue (uses the human site URL, not the OAuth API base). */
    public String browseUrl(String browseBase, String issueKey) {
        return browseBase + "/browse/" + issueKey;
    }

    /**
     * Fills every create-screen field the project marks {@code required} without a default and that the
     * base payload does not already cover, so project-specific mandatory custom fields (e.g. a required
     * "Criterios de aceptación") don't 400 the create. {@code string} fields get {@code fallbackText};
     * rich-text {@code doc} fields get a single-paragraph ADF doc. Other types (options, numbers, users…)
     * cannot be guessed generically and are left unset — Jira's diagnosable 400 then names them.
     */
    private void fillRequiredCustomFields(JiraApiContext ctx, String projectKey, String issueTypeId,
                                          Map<String, Object> fields, String fallbackText) {
        String text = fallbackText == null || fallbackText.isBlank() ? "See description." : fallbackText;
        for (CreateField field : listCreateFields(ctx, projectKey, issueTypeId)) {
            String id = field.fieldId();
            if (id == null || fields.containsKey(id) || BASE_FIELDS.contains(id)
                    || !Boolean.TRUE.equals(field.required())
                    || Boolean.TRUE.equals(field.hasDefaultValue())) {
                continue;
            }
            String type = field.schema() == null ? null : field.schema().type();
            String custom = field.schema() == null ? null : field.schema().custom();
            // Rich-text fields need an ADF doc even when the schema type says "string": the v3 API
            // requires ADF for textarea/paragraph custom fields (team-managed projects report them as
            // string+custom:…textarea, and a plain string is rejected as "not valid ADF").
            boolean richText = "doc".equals(type)
                    || (custom != null && (custom.contains("textarea") || custom.contains("paragraph")));
            if (richText) {
                fields.put(id, Map.of("type", "doc", "version", 1, "content",
                        List.of(Map.of("type", "paragraph", "content",
                                List.of(Map.of("type", "text", "text", text))))));
            } else if ("string".equals(type)) {
                fields.put(id, text);
            }
        }
    }

    // Helpers

    private static String enc(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    /**
     * Maps an error response inside the RestClient exchange, reading Jira's error body so the thrown
     * exception is DIAGNOSABLE. {@code onCreate} selects the 400/404 → push-failed mapping. The body is
     * parsed for Jira's {@code errorMessages} array and field {@code errors} map (token-free — bodies never
     * carry credentials). Throwing here aborts the call with a mapped exception.
     */
    private static RuntimeException mapError(org.springframework.http.client.ClientHttpResponse res,
                                             boolean onCreate) throws IOException {
        int status = res.getStatusCode().value();
        String detail = readJiraError(res);
        if (status == 401 || status == 403) {
            return IntegrationsInfrastructureExceptions.jiraAuthFailed();
        }
        if (onCreate && (status == 400 || status == 404)) {
            return IntegrationsInfrastructureExceptions.jiraPushFailed(
                    "Jira rejected the request (" + status + ")" + (detail.isBlank() ? "" : ": " + detail));
        }
        return IntegrationsInfrastructureExceptions.jiraUnreachable(
                "HTTP " + status + (detail.isBlank() ? "" : ": " + detail), null);
    }

    /**
     * Extracts Jira's {@code errorMessages} (array) and {@code errors} (field → message map) from an error
     * response body into a compact, token-free string. Returns "" when the body is empty or unparseable.
     */
    private static String readJiraError(org.springframework.http.client.ClientHttpResponse res) {
        try {
            byte[] raw = res.getBody().readAllBytes();
            if (raw.length == 0) {
                return "";
            }
            JsonNode body = ERROR_MAPPER.readTree(raw);
            List<String> parts = new ArrayList<>();
            JsonNode messages = body.get("errorMessages");
            if (messages != null && messages.isArray()) {
                messages.forEach(m -> parts.add(m.asText()));
            }
            JsonNode errors = body.get("errors");
            if (errors != null && errors.isObject()) {
                errors.fields().forEachRemaining(e -> parts.add(e.getKey() + ": " + e.getValue().asText()));
            }
            return String.join("; ", parts);
        } catch (Exception e) {
            return "";
        }
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

    // Jackson-bound response records

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Myself(String displayName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraProject(String key, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProjectSearch(List<JiraProject> values) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraIssueType(String id, String name) {}

    /** Response of {@code /issue/createmeta/{projectKey}/issuetypes}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateMetaIssueTypes(List<JiraIssueType> issueTypes) {}

    /** One create-screen field from {@code createmeta/{project}/issuetypes/{typeId}}. */
    public record CreateField(String fieldId, String name, Boolean required, Boolean hasDefaultValue,
                              FieldSchema schema) {}

    /**
     * The schema of a create-screen field. {@code type} is the value type ({@code string}, {@code doc},
     * {@code array}, …); {@code custom} names the custom-field kind (e.g. {@code …:textarea}) — needed
     * because rich-text fields report {@code type=string} but the v3 API requires ADF values for them.
     */
    public record FieldSchema(String type, String custom) {}

    private record CreateMetaFields(List<CreateField> fields) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatedIssue(String id, String key, String self) {}

    /** One issue returned by {@code /search/jql} with the subset of fields requested above. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraIssue(String key, IssueFields fields) {}

    /**
     * The requested field subset of a search hit. {@code description} is an ADF document (nested object)
     * bound as a {@code Map} — {@link JiraAdfReader} flattens it to plain text for parsing.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssueFields(String summary, Map<String, Object> description,
                              NamedRef issuetype, NamedRef priority) {}

    /** A Jira {name,id} reference (issue type, priority). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NamedRef(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IssueSearchResponse(List<JiraIssue> issues, Boolean isLast, String nextPageToken) {}

    /** One page of a token-paginated JQL search. */
    public record IssueSearchPage(List<JiraIssue> issues, boolean isLast, String nextPageToken) {}
}
