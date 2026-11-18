package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.SessionPurpose;

import java.util.UUID;

/**
 * Intent to create a discovery session (in {@code DRAFT}) under a project.
 *
 * @param projectId   the project the session belongs to (workspace context; plain id, no FK)
 * @param title       descriptive session title
 * @param language    BCP-47 meeting language (e.g. {@code es-PE})
 * @param purpose     what the meeting is for; a discovery meeting when not stated
 */
public record CreateDiscoverySessionCommand(UUID projectId, String title, String language, SessionPurpose purpose) {

    /** A discovery meeting. */
    public CreateDiscoverySessionCommand(UUID projectId, String title, String language) {
        this(projectId, title, language, SessionPurpose.DISCOVERY);
    }
}
