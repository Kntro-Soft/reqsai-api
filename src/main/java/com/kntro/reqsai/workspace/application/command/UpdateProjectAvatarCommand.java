package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/**
 * Replaces a project's avatar with an uploaded image.
 *
 * @param organizationId the organization the project belongs to
 * @param projectId      the project whose avatar is replaced
 * @param requestedBy    the authenticated user performing the change
 * @param bytes          the validated image bytes
 * @param contentType    the image content type
 */
public record UpdateProjectAvatarCommand(UUID organizationId, UUID projectId, UUID requestedBy, byte[] bytes, String contentType) {}
