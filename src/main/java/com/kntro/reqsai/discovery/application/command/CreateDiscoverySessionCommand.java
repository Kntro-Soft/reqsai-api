package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Intent to create a discovery session (in {@code DRAFT}) under a project.
 *
 * @param projectId   the project the session belongs to (workspace context; plain id, no FK)
 * @param title       descriptive session title
 * @param language    BCP-47 meeting language (e.g. {@code es-PE})
 */
public record CreateDiscoverySessionCommand(UUID projectId, String title, String language) {
}
