package com.kntro.reqsai.codebase.infrastructure.github;

import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads GitHub repositories through the REST API ({@code reqsai.codebase.github.api-url}, api.github.com by
 * default). Public repositories need no token; a private one needs a fine-grained token with read-only
 * "Contents" access, sent as a Bearer header and never logged. The archive is the repository's zipball,
 * streamed to a temporary file under a size cap and read entry by entry: only the files the caller keeps
 * are decoded, binaries are skipped, and the temporary file is always deleted.
 */
@Component
@Slf4j
class GitHubCodeHostAdapter implements CodeHostPort {

    /** Text read from one archive at most, whatever the per-file limits allow (zip-bomb guard). */
    static final long MAX_TOTAL_TEXT_BYTES = 64L * 1024 * 1024;

    private final HttpClient http;
    private final String apiUrl;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    GitHubCodeHostAdapter(@Value("${reqsai.codebase.github.api-url:https://api.github.com}") String apiUrl,
                          @Value("${reqsai.codebase.github.timeout:PT60S}") Duration timeout,
                          ObjectMapper objectMapper) {
        this.apiUrl = apiUrl.replaceAll("/+$", "");
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public RemoteRepository describe(String owner, String name, @Nullable String token) {
        HttpResponse<String> response = send(get("/repos/%s/%s".formatted(enc(owner), enc(name)), token,
                "application/vnd.github+json"), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode(), response.headers().firstValue("x-ratelimit-remaining"), owner + "/" + name,
                token);
        JsonNode root = objectMapper.readTree(response.body());
        String realOwner = root.path("owner").path("login").asString(owner);
        String realName = root.path("name").asString(name);
        String defaultBranch = root.path("default_branch").asString("main");
        String htmlUrl = root.path("html_url").asString("https://github.com/" + owner + "/" + name);
        return new RemoteRepository(realOwner, realName, defaultBranch, htmlUrl, root.path("private").asBoolean(false));
    }

    @Override
    public String headCommit(String owner, String name, String branch, @Nullable String token) {
        HttpResponse<String> response = send(get("/repos/%s/%s/commits/%s".formatted(enc(owner), enc(name),
                enc(branch)), token, "application/vnd.github.sha"), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 422) {
            throw CodebaseExceptions.notFound(owner + "/" + name + "@" + branch);
        }
        check(response.statusCode(), response.headers().firstValue("x-ratelimit-remaining"),
                owner + "/" + name + "@" + branch, token);
        String sha = response.body().strip();
        if (!sha.matches("[0-9a-f]{7,64}")) {
            throw CodebaseExceptions.hostUnavailable("unexpected commit reply");
        }
        return sha;
    }

    @Override
    public RepositoryArchive download(String owner, String name, String ref, @Nullable String token,
                                      ArchiveLimits limits, Predicate<String> keep) {
        Path archive = null;
        try {
            archive = Files.createTempFile("reqsai-repo-", ".zip");
            HttpResponse<InputStream> response = send(get("/repos/%s/%s/zipball/%s".formatted(enc(owner),
                    enc(name), enc(ref)), token, "application/vnd.github+json"), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    check(response.statusCode(), response.headers().firstValue("x-ratelimit-remaining"),
                            owner + "/" + name + "@" + ref, token);
                }
            }
            copyCapped(response.body(), archive, limits.maxDownloadBytes());
            return read(archive, limits, keep);
        } catch (IOException e) {
            throw CodebaseExceptions.hostUnavailable("could not read the repository archive");
        } finally {
            if (archive != null) {
                try {
                    Files.deleteIfExists(archive);
                } catch (IOException e) {
                    log.debug("Could not delete temporary archive {}", archive);
                }
            }
        }
    }

    private RepositoryArchive read(Path archive, ArchiveLimits limits, Predicate<String> keep) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        int skipped = 0;
        boolean truncated = false;
        long totalBytes = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String path = stripRoot(entry.getName());
                if (path.isEmpty() || path.contains("..") || !keep.test(path)) {
                    skipped++;
                    continue;
                }
                if (entry.getSize() > limits.maxFileBytes()) {
                    skipped++;
                    continue;
                }
                if (files.size() >= limits.maxFiles() || totalBytes >= MAX_TOTAL_TEXT_BYTES) {
                    truncated = true;
                    break;
                }
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = in.readNBytes((int) limits.maxFileBytes() + 1);
                }
                if (bytes.length > limits.maxFileBytes() || isBinary(bytes)) {
                    skipped++;
                    continue;
                }
                totalBytes += bytes.length;
                files.add(new SourceFile(path, new String(bytes, StandardCharsets.UTF_8)));
            }
        }
        return new RepositoryArchive(files, skipped, truncated);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(request, handler);
        } catch (IOException e) {
            throw CodebaseExceptions.hostUnavailable("GitHub could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw CodebaseExceptions.hostUnavailable("interrupted");
        }
    }

    private HttpRequest get(String path, @Nullable String token, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path))
                .timeout(timeout)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ReqsAI")
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.strip());
        }
        return builder.build();
    }

    /** Maps GitHub's answer to the codebase errors: 404 not found, 401/403 token or rate limit, 5xx outage. */
    private static void check(int status, Optional<String> rateLimitRemaining, String what, @Nullable String token) {
        if (status >= 200 && status < 300) return;
        if (status == 404) throw CodebaseExceptions.notFound(what);
        if (status == 401) throw CodebaseExceptions.accessDenied();
        if (status == 403 || status == 429) {
            if (status == 429 || rateLimitRemaining.filter("0"::equals).isPresent()) {
                throw CodebaseExceptions.hostUnavailable("GitHub rate limit reached, try again later");
            }
            if (token != null && !token.isBlank()) throw CodebaseExceptions.accessDenied();
            throw CodebaseExceptions.notFound(what);
        }
        throw CodebaseExceptions.hostUnavailable("GitHub answered " + status);
    }

    private static void copyCapped(InputStream in, Path target, long maxBytes) throws IOException {
        try (InputStream source = in; OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw CodebaseExceptions.tooLarge("the archive exceeds " + (maxBytes / (1024 * 1024)) + " MB");
                }
                out.write(buffer, 0, read);
            }
        }
    }

    /** {@code owner-repo-sha/src/App.tsx} → {@code src/App.tsx}. */
    static String stripRoot(String entryName) {
        String name = entryName.replace('\\', '/');
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(slash + 1);
    }

    private static boolean isBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    private static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }
}
