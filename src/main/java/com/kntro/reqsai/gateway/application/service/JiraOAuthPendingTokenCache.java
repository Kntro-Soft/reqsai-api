package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.OAuthTokens;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.Site;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived in-memory cache of a completed OAuth code exchange, keyed by the signed {@code state}
 * (ADR-0023).
 * <p>
 * Atlassian authorization codes are SINGLE-USE: the multi-site callback exchanges the code once (to call
 * accessible-resources) and, when the user must still pick a site, cannot exchange it again on the second
 * callback. The already-exchanged tokens + discovered sites are cached here so the second callback (with
 * the chosen {@code cloudId}) completes from the cache without re-consuming the code. Entries expire after
 * {@link #TTL} (a few minutes — long enough to pick a site, short enough to bound retention) and are
 * removed on use. Tokens live only in memory and are never logged.
 */
@Component
public class JiraOAuthPendingTokenCache {

    /** How long an unfinished multi-site selection is retained before the user must restart the flow. */
    static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, Entry> byState = new ConcurrentHashMap<>();

    /** Caches the exchanged tokens + discovered sites under {@code state}. */
    public void put(String state, OAuthTokens tokens, List<Site> sites) {
        byState.put(state, new Entry(tokens, sites, Instant.now().plus(TTL)));
    }

    /** Returns the cached exchange for {@code state}, or null if absent/expired (expired entries are purged). */
    public @Nullable Pending get(String state) {
        purgeExpired();
        Entry entry = byState.get(state);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            byState.remove(state);
            return null;
        }
        return new Pending(entry.tokens, entry.sites);
    }

    /** Removes the cached exchange for {@code state} (called once the connection is saved). */
    public void evict(String state) {
        byState.remove(state);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        byState.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    /** A cached, already-exchanged token set + the sites it can reach. */
    public record Pending(OAuthTokens tokens, List<Site> sites) {}

    private record Entry(OAuthTokens tokens, List<Site> sites, Instant expiresAt) {}
}
