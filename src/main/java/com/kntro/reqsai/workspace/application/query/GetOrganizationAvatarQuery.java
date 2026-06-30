package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

/** Query to retrieve an organization's stored avatar bytes by organization id. */
public record GetOrganizationAvatarQuery(UUID organizationId) {
}
