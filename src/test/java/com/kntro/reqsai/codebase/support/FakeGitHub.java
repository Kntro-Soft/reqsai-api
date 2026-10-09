package com.kntro.reqsai.codebase.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A minimal GitHub REST API for tests: repository metadata, the commit a branch points at, and the zipball
 * (redirected to a codeload-like URL, like GitHub does). A private repository answers 404 unless the request
 * carries its token. Files can be changed between runs to test reindexing.
 */
public final class FakeGitHub implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Repo> repos = new ConcurrentHashMap<>();
    private final AtomicInteger zipDownloads = new AtomicInteger();

    public record Repo(String owner, String name, String branch, boolean isPrivate, String token,
                       Map<String, String> files, String sha) {
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

    public void put(String owner, String name, String branch, boolean isPrivate, String token,
                    Map<String, String> files) {
        String sha = Integer.toHexString(files.hashCode() & 0x7fffffff) + "abcdef0";
        repos.put(key(owner, name), new Repo(owner, name, branch, isPrivate, token, new LinkedHashMap<>(files), sha));
    }

    private static String key(String owner, String name) {
        return owner.toLowerCase() + "/" + name.toLowerCase();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String[] parts = exchange.getRequestURI().getPath().split("/");
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
            send(exchange, 404, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        Repo repo = repos.get(key(parts[2], parts[3]));
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (repo == null || (repo.isPrivate() && !("Bearer " + repo.token()).equals(auth))) {
            send(exchange, auth != null && repo != null ? 401 : 404, "application/json",
                    "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (parts.length == 4) {
            String json = """
                    {"name":"%s","owner":{"login":"%s"},"default_branch":"%s","private":%s,"html_url":"%s/%s/%s"}
                    """.formatted(repo.name(), repo.owner(), repo.branch(), repo.isPrivate(), apiUrl(), repo.owner(),
                    repo.name());
            send(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("commits".equals(parts[4])) {
            String branch = String.join("/", java.util.Arrays.copyOfRange(parts, 5, parts.length));
            if (!branch.equals(repo.branch())) {
                send(exchange, 422, "application/json", "{\"message\":\"No commit\"}".getBytes(StandardCharsets.UTF_8));
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
        send(exchange, 404, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
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
