package com.kntro.reqsai.codebase.application.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes credentials that slipped into source code before any of it reaches the AI model: private
 * keys, cloud, payment and code-host tokens, JWTs, connection strings with a password, and values
 * assigned to variables named like a secret.
 */
public final class SecretRedactor {

    static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> TOKENS = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b"),
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{40,}\\b"),
            Pattern.compile("\\bglpat-[A-Za-z0-9_-]{20,}\\b"),
            Pattern.compile("\\bxox[abposr]-[A-Za-z0-9-]{10,}\\b"),
            Pattern.compile("\\b(?:sk|rk|pk)_(?:live|test)_[A-Za-z0-9]{16,}\\b"),
            Pattern.compile("\\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\\b"),
            Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"));

    /** {@code scheme://user:password@host} — keeps the user and host, drops the password. */
    private static final Pattern URL_PASSWORD = Pattern.compile("(\\b[a-z][a-z0-9+.-]*://[^\\s:/@]+:)[^\\s@/]+@");

    /** {@code apiKey = "…"}, {@code "password": "…"}, {@code SECRET_TOKEN: …} (a value of 6+ chars). */
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?i)((?:api[_-]?key|secret|passw(?:or)?d|pwd|token|auth[_-]?key|private[_-]?key|client[_-]?secret)"
                    + "[A-Za-z0-9_]*[\"']?\\s*[:=]\\s*[\"'])([^\"'\\s]{6,})([\"'])");

    private SecretRedactor() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static String redact(String content) {
        String out = content;
        for (Pattern token : TOKENS) {
            out = token.matcher(out).replaceAll(REDACTED);
        }
        out = URL_PASSWORD.matcher(out).replaceAll("$1" + REDACTED + "@");
        out = ASSIGNED_SECRET.matcher(out).replaceAll("$1" + REDACTED + "$3");
        return out;
    }
}
