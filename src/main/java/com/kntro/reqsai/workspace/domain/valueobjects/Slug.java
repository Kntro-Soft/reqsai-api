package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import org.jspecify.annotations.NonNull;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * URL-safe organization identifier ({@code acme-corp}) — immutable after creation and unique across the
 * platform; it names the tenant schema ({@code tenant_<slug>}).
 */
public record Slug(String value) {

    private static final int MAX = 50;
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public Slug {
        if (value == null || value.isBlank()) {
            throw Exceptions.invalidValue("slug", "cannot be null or blank");
        }
        value = value.trim().toLowerCase();
        if (value.length() > MAX) {
            throw Exceptions.invalidValue("slug", "must be at most %d characters".formatted(MAX));
        }
        if (!PATTERN.matcher(value).matches()) {
            throw Exceptions.invalidValue("slug", "'%s' must be lowercase alphanumeric words separated by single hyphens".formatted(value));
        }
    }

    public static Slug of(String value) {
        return new Slug(value);
    }

    /** Derives a slug from a free-text name (strip accents, lowercase, hyphenate). */
    public static Slug fromName(String name) {
        String stripped = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = stripped.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > MAX) {
            slug = slug.substring(0, MAX).replaceAll("-+$", "");
        }
        return new Slug(slug);
    }

    @Override
    public @NonNull String toString() {
        return value;
    }
}
