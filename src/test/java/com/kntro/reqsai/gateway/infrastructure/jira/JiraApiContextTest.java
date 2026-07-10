package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.gateway.infrastructure.jira.JiraClient.JiraApiContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Infrastructure: dual-mode Jira API base URL + auth selection")
class JiraApiContextTest {

    @Test
    @DisplayName("API_TOKEN mode uses the site base URL and Basic auth")
    void api_token_context() {
        JiraApiContext ctx = JiraApiContext.apiToken("https://acme.atlassian.net", "pm@acme.com", "tok");

        assertThat(ctx.apiBase()).isEqualTo("https://acme.atlassian.net/rest/api/3");
        assertThat(ctx.browseBase()).isEqualTo("https://acme.atlassian.net");
        assertThat(ctx.authHeader()).startsWith("Basic ");
        String decoded = new String(Base64.getDecoder().decode(ctx.authHeader().substring("Basic ".length())),
                StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("pm@acme.com:tok");
    }

    @Test
    @DisplayName("OAUTH2 mode uses the api.atlassian.com/ex/jira/{cloudId} base and Bearer auth")
    void oauth_context() {
        JiraApiContext ctx = JiraApiContext.oauth("cloud-1", "access-abc", "https://acme.atlassian.net");

        assertThat(ctx.apiBase()).isEqualTo("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3");
        assertThat(ctx.browseBase()).isEqualTo("https://acme.atlassian.net"); // browse links use the human site
        assertThat(ctx.authHeader()).isEqualTo("Bearer access-abc");
    }
}
