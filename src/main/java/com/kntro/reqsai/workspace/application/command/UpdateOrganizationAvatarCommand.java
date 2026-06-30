package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/**
 * Replaces an organization's avatar with an uploaded image.
 *
 * @param organizationId the organization whose avatar is replaced
 * @param requestedBy    the authenticated user performing the change (must be owner or admin)
 * @param bytes          the validated image bytes
 * @param contentType    the image content type
 */
public record UpdateOrganizationAvatarCommand(UUID organizationId, UUID requestedBy, byte[] bytes, String contentType) {}
