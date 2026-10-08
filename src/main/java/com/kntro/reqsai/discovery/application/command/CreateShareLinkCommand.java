package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * The analyst opens a link so a client can review the project's stories without an account.
 *
 * @param projectId the project whose stories the link shows
 * @param days      how many days the link stays valid
 */
public record CreateShareLinkCommand(UUID projectId, int days) {
}
