package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.domain.exception.CodebaseExceptions;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GitHub repository as the analyst typed it: {@code owner/name}, or a github.com URL (with or without
 * {@code .git}, {@code /tree/<branch>} or a trailing slash). A branch in the URL is kept as a hint.
 */
public record RepositoryReference(String owner, String name, @Nullable String branch) {

    private static final String SEGMENT = "[A-Za-z0-9](?:[A-Za-z0-9._-]{0,99})";
    private static final Pattern SHORT = Pattern.compile("^(" + SEGMENT + ")/(" + SEGMENT + ")$");
    private static final Pattern URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/(" + SEGMENT + ")/(" + SEGMENT + ")"
                    + "(?:/tree/([^?#]+))?/?(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);

    public static RepositoryReference parse(@Nullable String input) {
        String value = input == null ? "" : input.strip();
        Matcher url = URL.matcher(value);
        if (url.matches()) {
            return of(url.group(1), url.group(2), url.group(3), value);
        }
        Matcher shortForm = SHORT.matcher(value);
        if (shortForm.matches()) {
            return of(shortForm.group(1), shortForm.group(2), null, value);
        }
        throw CodebaseExceptions.urlInvalid(value);
    }

    private static RepositoryReference of(String owner, String rawName, @Nullable String branch, String input) {
        String name = rawName.endsWith(".git") ? rawName.substring(0, rawName.length() - 4) : rawName;
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw CodebaseExceptions.urlInvalid(input);
        }
        String cleanBranch = branch == null || branch.isBlank() ? null : branch.replaceAll("/+$", "");
        return new RepositoryReference(owner, name, cleanBranch);
    }
}
