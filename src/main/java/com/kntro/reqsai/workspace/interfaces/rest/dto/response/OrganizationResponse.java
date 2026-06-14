package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import java.util.UUID;

/**
 * Flat response view of an organization. Scalar fields only — generation settings are inlined
 * ({@code meetingLanguage}, {@code audioRetentionDays}) rather than nested. Plan limits are
 * billing-derived and exposed via {@code GET}, not here.
 */
public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String status,
        UUID ownerId,
        String meetingLanguage,
        int audioRetentionDays
) {
}
