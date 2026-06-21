package com.kntro.reqsai.iam.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * Navigation preferences for a user (TS17). Immutable value object, mapped as an {@code @Embeddable}
 * so its fields land directly on {@code public.users}: {@code lastVisitedOrgId} →
 * {@code last_visited_org_id}, {@code lastVisitedProjectId} → {@code last_visited_project_id}.
 * Both are nullable — populated lazily once the user navigates into an org/project.
 */
@Embeddable
public record UserPreferences(

        @Column(name = "last_visited_org_id", columnDefinition = "uuid")
        UUID lastVisitedOrgId,

        @Column(name = "last_visited_project_id", columnDefinition = "uuid")
        UUID lastVisitedProjectId
) {

    /** Blank preferences for a newly registered user who hasn't navigated anywhere yet. */
    public static UserPreferences empty() {
        return new UserPreferences(null, null);
    }

    public static UserPreferences of(UUID lastVisitedOrgId, UUID lastVisitedProjectId) {
        return new UserPreferences(lastVisitedOrgId, lastVisitedProjectId);
    }
}
