package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * The analyst stops a share link from working before it expires.
 *
 * @param projectId the project the link belongs to
 * @param linkId    the link to revoke
 */
public record RevokeShareLinkCommand(UUID projectId, UUID linkId) {
}
