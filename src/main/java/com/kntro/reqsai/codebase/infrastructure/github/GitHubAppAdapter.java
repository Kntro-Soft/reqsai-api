package com.kntro.reqsai.codebase.infrastructure.github;

import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReqsAI's GitHub App over the REST API. The App authenticates with a short JWT signed by its private key
 * (RS256, issued for the client id) and exchanges it for installation tokens, which are cached until five
 * minutes before they expire and never stored. The OAuth code of the install redirect is exchanged only to
 * learn which installations the user can access, and that user token is dropped right after. No token is
 * ever logged.
 */
@Component
@Slf4j
class GitHubAppAdapter implements GitHubAppPort {

    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final int MAX_REPOSITORIES = 300;
    private static final String API_VERSION = "2022-11-28";

    private final GitHubAppProperties props;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Map<Long, CachedToken> tokens = new ConcurrentHashMap<>();
    private volatile @Nullable PrivateKey key;

    GitHubAppAdapter(GitHubAppProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public String installUrl(String state) {
        requireConfigured();
        return props.webUrl + "/apps/" + enc(props.slug) + "/installations/new?state=" + enc(state);
    }

    @Override
    public Set<Long> installationsOfUser(String oauthCode) {
        requireConfigured();
        String body = json.writeValueAsString(Map.of(
                "client_id", props.clientId, "client_secret", props.clientSecret, "code", oauthCode));
        HttpResponse<String> exchanged = send(HttpRequest.newBuilder(URI.create(props.webUrl + "/login/oauth/access_token"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "ReqsAI")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        JsonNode reply = exchanged.statusCode() == 200 ? json.readTree(exchanged.body()) : null;
        String userToken = reply == null ? "" : reply.path("access_token").asString("");
        if (userToken.isBlank()) {
            throw CodebaseExceptions.installationForbidden("GitHub did not confirm who installed the App");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            HttpResponse<String> response = send(get("/user/installations?per_page=%d&page=%d"
                    .formatted(PAGE_SIZE, page), userToken));
            check(response, "the user's installations");
            JsonNode installations = json.readTree(response.body()).path("installations");
            for (JsonNode installation : installations) ids.add(installation.path("id").asLong());
            if (installations.size() < PAGE_SIZE) break;
        }
        return ids;
    }

    @Override
    public Installation installation(long installationId) {
        requireConfigured();
        HttpResponse<String> response = send(get("/app/installations/" + installationId, appJwt()));
        if (response.statusCode() == 404) throw CodebaseExceptions.installationNotFound(installationId);
        check(response, "installation " + installationId);
        JsonNode root = json.readTree(response.body());
        JsonNode account = root.path("account");
        return new Installation(
                root.path("id").asLong(installationId),
                account.path("login").asString(""),
                account.path("type").asString("User"),
                text(root.path("repository_selection")),
                text(root.path("html_url")),
                instant(root.path("suspended_at")));
    }

    @Override
    public String token(long installationId) {
        requireConfigured();
        CachedToken cached = tokens.get(installationId);
        if (cached != null && Instant.now().isBefore(cached.expiresAt().minus(TOKEN_MARGIN))) {
            return cached.value();
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(
                        props.apiUrl + "/app/installations/" + installationId + "/access_tokens"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "ReqsAI")
                .header("Authorization", "Bearer " + appJwt())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
        if (response.statusCode() == 404) throw CodebaseExceptions.installationNotFound(installationId);
        if (response.statusCode() == 403) throw CodebaseExceptions.accessDenied();
        check(response, "a token for installation " + installationId);
        JsonNode root = json.readTree(response.body());
        String value = root.path("token").asString("");
        if (value.isBlank()) throw CodebaseExceptions.hostUnavailable("GitHub returned no installation token");
        Instant expiresAt = instant(root.path("expires_at"));
        tokens.put(installationId, new CachedToken(value,
                expiresAt != null ? expiresAt : Instant.now().plus(Duration.ofMinutes(50))));
        return value;
    }

    @Override
    public List<AppRepository> repositories(long installationId) {
        String token = token(installationId);
        List<AppRepository> repositories = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES && repositories.size() < MAX_REPOSITORIES; page++) {
            HttpResponse<String> response = send(get("/installation/repositories?per_page=%d&page=%d"
                    .formatted(PAGE_SIZE, page), token));
            check(response, "the repositories of installation " + installationId);
            JsonNode items = json.readTree(response.body()).path("repositories");
            for (JsonNode repo : items) {
                String owner = repo.path("owner").path("login").asString("");
                String name = repo.path("name").asString("");
                if (owner.isBlank() || name.isBlank()) continue;
                repositories.add(new AppRepository(owner, name,
                        repo.path("default_branch").asString("main"),
                        repo.path("html_url").asString(props.webUrl + "/" + owner + "/" + name),
                        repo.path("private").asBoolean(false),
                        text(repo.path("description")),
                        instant(repo.path("pushed_at"))));
            }
            if (items.size() < PAGE_SIZE) break;
        }
        repositories.sort(Comparator.comparing(AppRepository::pushedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return repositories.size() > MAX_REPOSITORIES ? repositories.subList(0, MAX_REPOSITORIES) : repositories;
    }

    @Override
    public boolean verifySignature(byte[] payload, @Nullable String signature) {
        if (props.webhookSecret.isEmpty() || signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(payload)).getBytes(StandardCharsets.US_ASCII);
            byte[] provided = signature.substring("sha256=".length()).strip().toLowerCase()
                    .getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, provided);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook HMAC failed", e);
        }
    }

    /** The App's own credential: a JWT valid for 9 minutes, backdated a minute against clock drift. */
    private String appJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.clientId)
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(540)))
                .signWith(privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey privateKey() {
        PrivateKey current = key;
        if (current == null) {
            current = PemKeys.rsaPrivateKey(props.privateKey);
            key = current;
        }
        return current;
    }

    private void requireConfigured() {
        if (!props.isConfigured()) throw CodebaseExceptions.appNotConfigured();
    }

    private HttpRequest get(String path, String bearer) {
        return HttpRequest.newBuilder(URI.create(props.apiUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "ReqsAI")
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw CodebaseExceptions.hostUnavailable("GitHub could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw CodebaseExceptions.hostUnavailable("interrupted");
        }
    }

    private static void check(HttpResponse<String> response, String what) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;
        if (status == 401 || status == 403) {
            boolean limited = response.headers().firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent();
            if (limited) throw CodebaseExceptions.hostUnavailable("GitHub rate limit reached, try again later");
            throw CodebaseExceptions.accessDenied();
        }
        if (status == 429) throw CodebaseExceptions.hostUnavailable("GitHub rate limit reached, try again later");
        log.warn("GitHub answered {} for {}", status, what);
        throw CodebaseExceptions.hostUnavailable("GitHub answered " + status);
    }

    private static @Nullable String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String value = node.asString("");
        return value.isBlank() ? null : value;
    }

    private static @Nullable Instant instant(JsonNode node) {
        String value = text(node);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
