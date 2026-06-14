package com.kntro.reqsai.workspace.application.command;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Intent to create a new organization (and provision its tenant). {@code slug} and {@code meetingLanguage}
 * are optional — the slug is derived from the name and the language defaults when omitted.
 *
 * @param name            visible organization name
 * @param slug            optional URL slug (derived from {@code name} if {@code null})
 * @param meetingLanguage optional BCP-47 default meeting language (defaults when {@code null})
 * @param requestedBy     id of the authenticated user creating the org (becomes the owner)
 */
public record CreateOrganizationCommand(
        String name,
        @Nullable String slug,
        @Nullable String meetingLanguage,
        UUID requestedBy
) {
}
