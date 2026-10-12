package com.kntro.reqsai.codebase.infrastructure.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The GitHub App's settings ({@code reqsai.codebase.github.app.*}). The App is usable when its slug, client
 * id, client secret, private key and webhook secret are all set: a connected repository is promised to
 * update on every push, so the webhooks must be verifiable.
 */
@Component
class GitHubAppProperties {

    final String apiUrl;
    final String webUrl;
    final String slug;
    final String clientId;
    final String clientSecret;
    final String privateKey;
    final String webhookSecret;

    GitHubAppProperties(@Value("${reqsai.codebase.github.api-url:https://api.github.com}") String apiUrl,
                        @Value("${reqsai.codebase.github.web-url:https://github.com}") String webUrl,
                        @Value("${reqsai.codebase.github.app.slug:}") String slug,
                        @Value("${reqsai.codebase.github.app.client-id:}") String clientId,
                        @Value("${reqsai.codebase.github.app.client-secret:}") String clientSecret,
                        @Value("${reqsai.codebase.github.app.private-key:}") String privateKey,
                        @Value("${reqsai.codebase.github.app.webhook-secret:}") String webhookSecret) {
        this.apiUrl = apiUrl.strip().replaceAll("/+$", "");
        this.webUrl = webUrl.strip().replaceAll("/+$", "");
        this.slug = slug.strip();
        this.clientId = clientId.strip();
        this.clientSecret = clientSecret.strip();
        this.privateKey = privateKey.strip();
        this.webhookSecret = webhookSecret.strip();
    }

    boolean isConfigured() {
        return !slug.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty() && !privateKey.isEmpty()
                && !webhookSecret.isEmpty();
    }
}
