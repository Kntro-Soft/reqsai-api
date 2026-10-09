package com.kntro.reqsai.discovery.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * A module of the client's code a suggestion or story relates to: the repository ({@code owner/name}), the
 * module's folder, its business name and a link to it on the code host. Stored as JSON.
 */
public record CodeReference(String repository, String path, String name, @Nullable String url) {
}
