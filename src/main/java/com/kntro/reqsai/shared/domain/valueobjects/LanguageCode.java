package com.kntro.reqsai.shared.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import org.jspecify.annotations.NonNull;

import java.util.regex.Pattern;

/**
 * BCP-47 language tag (e.g. {@code es-PE}, {@code en-US}) — a cross-cutting value object reused by any
 * context that deals with a locale: the organization's default {@code meetingLanguage} (workspace) and a
 * {@code DiscoverySession}'s language (discovery). Self-validating, immutable, normalized to {@code ll-RR}.
 */
public record LanguageCode(String value) {

    /** Matches {@code ll} or {@code ll-RR} (ISO-639 language, optional ISO-3166 region). */
    private static final Pattern PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    public LanguageCode {
        if (value == null || value.isBlank()) {
            throw Exceptions.invalidValue("language", "cannot be null or blank");
        }
        value = normalize(value.trim());
        if (!PATTERN.matcher(value).matches()) {
            throw Exceptions.invalidValue("language", "'%s' is not a valid BCP-47 tag".formatted(value));
        }
    }

    private static String normalize(String raw) {
        String[] parts = raw.replace('_', '-').split("-");
        if (parts.length == 1) {
            return parts[0].toLowerCase();
        }
        return parts[0].toLowerCase() + "-" + parts[1].toUpperCase();
    }

    public static LanguageCode of(String value) {
        return new LanguageCode(value);
    }

    @Override
    public @NonNull String toString() {
        return value;
    }
}
