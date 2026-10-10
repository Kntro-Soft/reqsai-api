package com.kntro.reqsai.codebase.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A minimal GitHub for tests. The REST API: repository metadata, the commit a branch points at, and the zipball
 * (redirected to a codeload-like URL, like GitHub does). The GitHub App: installations, installation tokens
 * (only for a JWT signed with the App's key and issued for its client id), the repositories an installation
 * shares, and the OAuth exchange of the install redirect. A private repository answers 404 unless the request
 * carries the token of the installation that shares it. Files can be changed between runs to test reindexing.
 */
public final class FakeGitHub implements AutoCloseable {

    private static final Pattern JSON_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpServer server;
    private final Map<String, Repo> repos = new ConcurrentHashMap<>();
    private final Map<Long, Installation> installations = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> userCodes = new ConcurrentHashMap<>();
    private final AtomicInteger zipDownloads = new AtomicInteger();
    private final AtomicInteger tokensMinted = new AtomicInteger();
    private volatile String clientId = "";
    private volatile String clientSecret = "";
    private volatile PublicKey appKey;

    /** A repository; {@code installationId} is the installation that shares it, or null. */
    public record Repo(String owner, String name, String branch, boolean isPrivate, Long installationId,
                       Map<String, String> files, String sha) {
    }

    public record Installation(long id, String account, String type, boolean suspended) {
    }

    public FakeGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String apiUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int zipDownloads() {
        return zipDownloads.get();
    }

    public int tokensMinted() {
        return tokensMinted.get();
    }

    /** The GitHub App: its client id and secret, and the public half of its key. */
    public void app(String clientId, String clientSecret, PublicKey key) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.appKey = key;
    }

    public void installation(long id, String account, boolean suspended) {
        installations.put(id, new Installation(id, account, "Organization", suspended));
    }

    /** An OAuth code of the install redirect, and the installations its GitHub user can access. */
    public void userCode(String code, Long... installationIds) {
        userCodes.put(code, Set.of(installationIds));
    }

    public void put(String owner, String name, String branch, boolean isPrivate, Long installationId,
                    Map<String, String> files) {
        String sha = Integer.toHexString(files.hashCode() & 0x7fffffff) + "abcdef0";
        repos.put(key(owner, name), new Repo(owner, name, branch, isPrivate, installationId,
                new LinkedHashMap<>(files), sha));
    }

    public Repo repo(String owner, String name) {
        return repos.get(key(owner, name));
    }

    private static String key(String owner, String name) {
        return owner.toLowerCase() + "/" + name.toLowerCase();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (path.equals("/login/oauth/access_token")) {
            oauth(exchange);
            return;
        }
        if (path.equals("/user/installations")) {
            Set<Long> ids = auth != null && auth.startsWith("Bearer ghu_")
                    ? userCodes.getOrDefault(auth.substring("Bearer ghu_".length()), Set.of()) : Set.of();
            String items = ids.stream().map(id -> "{\"id\":%d,\"account\":{\"login\":\"%s\"}}"
                    .formatted(id, installations.containsKey(id) ? installations.get(id).account() : "x"))
                    .collect(Collectors.joining(","));
            send(exchange, auth == null ? 401 : 200, "{\"total_count\":%d,\"installations\":[%s]}".formatted(ids.size(), items));
            return;
        }
        if (parts.length >= 4 && "app".equals(parts[1]) && "installations".equals(parts[2])) {
            app(exchange, parts, auth);
            return;
        }
        if (path.equals("/installation/repositories")) {
            Long id = installationOf(auth);
            String items = repos.values().stream()
                    .filter(r -> id != null && id.equals(r.installationId()))
                    .map(r -> "{\"name\":\"%s\",\"owner\":{\"login\":\"%s\"},\"private\":%s,\"default_branch\":\"%s\","
                            .formatted(r.name(), r.owner(), r.isPrivate(), r.branch())
                            + "\"html_url\":\"%s/%s/%s\",\"pushed_at\":\"2026-10-01T12:00:00Z\"}"
                            .formatted(apiUrl(), r.owner(), r.name()))
                    .collect(Collectors.joining(","));
            send(exchange, id == null ? 401 : 200, "{\"total_count\":1,\"repositories\":[%s]}".formatted(items));
            return;
        }
        // /repos/{owner}/{name}[/commits/{branch...} | /zipball/{ref}] or /codeload/{owner}/{name}/{ref}.zip
        if (parts.length >= 4 && "codeload".equals(parts[1])) {
            Repo repo = repos.get(key(parts[2], parts[3]));
            if (repo == null) {
                send(exchange, 404, "text/plain", "missing".getBytes(StandardCharsets.UTF_8));
                return;
            }
            zipDownloads.incrementAndGet();
            send(exchange, 200, "application/zip", zip(repo));
            return;
        }
        if (parts.length < 4 || !"repos".equals(parts[1])) {
            send(exchange, 404, "{}");
            return;
        }
        Repo repo = repos.get(key(parts[2], parts[3]));
        if (repo == null || (repo.isPrivate() && !repo.installationId().equals(installationOf(auth)))) {
            boolean badCredentials = auth != null && installationOf(auth) == null;
            send(exchange, badCredentials ? 401 : 404, badCredentials ? "{\"message\":\"Bad credentials\"}"
                    : "{\"message\":\"Not Found\"}");
            return;
        }
        if (parts.length == 4) {
            send(exchange, 200, """
                    {"name":"%s","owner":{"login":"%s"},"default_branch":"%s","private":%s,"html_url":"%s/%s/%s"}
                    """.formatted(repo.name(), repo.owner(), repo.branch(), repo.isPrivate(), apiUrl(), repo.owner(),
                    repo.name()));
            return;
        }
        if ("commits".equals(parts[4])) {
            String branch = String.join("/", java.util.Arrays.copyOfRange(parts, 5, parts.length));
            if (!branch.equals(repo.branch())) {
                send(exchange, 422, "{\"message\":\"No commit\"}");
                return;
            }
            send(exchange, 200, "text/plain", repo.sha().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("zipball".equals(parts[4])) {
            exchange.getResponseHeaders().add("Location",
                    apiUrl() + "/codeload/" + repo.owner() + "/" + repo.name() + "/" + parts[5] + ".zip");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        send(exchange, 404, "{}");
    }

    /** {@code GET /app/installations/{id}} and {@code POST /app/installations/{id}/access_tokens}. */
    private void app(HttpExchange exchange, String[] parts, String auth) throws IOException {
        if (!validAppJwt(auth)) {
            send(exchange, 401, "{\"message\":\"A JSON web token could not be decoded\"}");
            return;
        }
        Installation installation = installations.get(Long.parseLong(parts[3]));
        if (installation == null) {
            send(exchange, 404, "{\"message\":\"Not Found\"}");
            return;
        }
        if (parts.length == 4) {
            send(exchange, 200, """
                    {"id":%d,"account":{"login":"%s","type":"%s"},"repository_selection":"selected",\
                    "html_url":"https://github.com/organizations/%s/settings/installations/%d","suspended_at":%s}
                    """.formatted(installation.id(), installation.account(), installation.type(),
                    installation.account(), installation.id(),
                    installation.suspended() ? "\"2026-10-01T00:00:00Z\"" : "null"));
            return;
        }
        if (installation.suspended()) {
            send(exchange, 403, "{\"message\":\"This installation has been suspended\"}");
            return;
        }
        tokensMinted.incrementAndGet();
        send(exchange, 201, "{\"token\":\"ghs_%d\",\"expires_at\":\"%s\"}"
                .formatted(installation.id(), Instant.now().plusSeconds(3600)));
    }

    private void oauth(HttpExchange exchange) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        Matcher m = JSON_FIELD.matcher(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        while (m.find()) body.put(m.group(1), m.group(2));
        boolean ok = clientId.equals(body.get("client_id")) && clientSecret.equals(body.get("client_secret"))
                && userCodes.containsKey(body.getOrDefault("code", ""));
        send(exchange, 200, ok ? "{\"access_token\":\"ghu_%s\",\"token_type\":\"bearer\"}".formatted(body.get("code"))
                : "{\"error\":\"bad_verification_code\"}");
    }

    /** RS256 over {@code header.payload} with the App's key, issued for its client id and not expired. */
    private boolean validAppJwt(String auth) {
        if (auth == null || !auth.startsWith("Bearer ") || appKey == null) return false;
        String[] jwt = auth.substring("Bearer ".length()).split("\\.");
        if (jwt.length != 3) return false;
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(appKey);
            rsa.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
            if (!rsa.verify(Base64.getUrlDecoder().decode(jwt[2]))) return false;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
        String payload = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
        Matcher exp = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)").matcher(payload);
        return payload.contains("\"iss\":\"" + clientId + "\"")
                && exp.find() && Long.parseLong(exp.group(1)) > Instant.now().getEpochSecond();
    }

    private static Long installationOf(String auth) {
        if (auth == null || !auth.startsWith("Bearer ghs_")) return null;
        try {
            return Long.parseLong(auth.substring("Bearer ghs_".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] zip(Repo repo) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            String root = repo.owner() + "-" + repo.name() + "-" + repo.sha().substring(0, 7) + "/";
            zip.putNextEntry(new ZipEntry(root));
            zip.closeEntry();
            for (Map.Entry<String, String> file : repo.files().entrySet()) {
                zip.putNextEntry(new ZipEntry(root + file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** The restaurant reservations app the tests (and the demo) connect: TypeScript + Express + PostgreSQL. */
    public static Map<String, String> restaurantApp() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", """
                # Reservas La Tradición

                Sistema de reservas en línea del restaurante La Tradición: los comensales reservan mesa desde la web y \
                el personal gestiona el salón y los pagos.
                """);
        files.put("package.json", """
                {"name":"reservas","dependencies":{"express":"^4.19.0","pg":"^8.11.0"},"devDependencies":{"typescript":"^5.4.0"}}
                """);
        files.put("package-lock.json", "{\"lockfileVersion\":3}");
        files.put(".env", "DATABASE_URL=postgres://app:SuperSecret123@db/app");
        files.put("node_modules/express/index.js", "module.exports = {}");
        files.put("src/reservations/reservation.routes.ts", """
                import { Router } from 'express';
                export const router = Router();
                router.post('/reservations', createReservationHandler);
                router.delete('/reservations/:id', cancelReservationHandler);
                """);
        files.put("src/reservations/cancellation.policy.ts", """
                /** Reservations can be cancelled only up to 2 hours before the booked time. */
                export const CANCELLATION_LIMIT_HOURS = 2;
                export function canCancel(startsAt: Date, now: Date): boolean {
                  return (startsAt.getTime() - now.getTime()) / 3_600_000 >= CANCELLATION_LIMIT_HOURS;
                }
                """);
        files.put("src/reservations/reservation.service.ts", """
                export const MAX_PARTY_SIZE = 8;
                export function createReservation(people: number) {
                  if (people > MAX_PARTY_SIZE) throw new Error('Máximo 8 personas por reserva');
                }
                export function cancelReservation(id: string) {
                  const apiKey = "%s";
                }
                """.formatted("sk_" + "live_" + "abcdefghijklmnopqrstuvwx"));
        files.put("src/payments/payment.routes.ts", """
                router.post('/payments', payHandler);
                """);
        files.put("src/payments/payment.service.ts", """
                export function payWithYape(amount: number) { }
                export function payWithCard(amount: number) { }
                """);
        files.put("db/migrations/001_init.sql", """
                CREATE TABLE reservations (id uuid primary key, starts_at timestamptz, people int);
                CREATE TABLE payments (id uuid primary key, reservation_id uuid);
                """);
        files.put("db/migrations/002_tables.sql", "create table if not exists tables (id uuid primary key);");
        return files;
    }
}
