package com.kntro.reqsai.shared.application.search;

import java.util.Set;
import java.util.UUID;

/**
 * The set of projects a caller may see within their organization, resolved once by the workspace
 * {@code search} port and handed to the project- and story-scoped searches so authorization is applied
 * uniformly and never leaks unauthorized rows.
 *
 * <p>{@code all == true} means an org owner/admin who sees every project (the concrete
 * {@code projectIds} set is then ignored). Otherwise the caller is limited to {@link #projectIds()};
 * an empty set means "no accessible projects".
 *
 * @param all        whether the caller sees all projects in the organization (owner/admin)
 * @param projectIds the explicitly accessible project ids (only consulted when not {@code all})
 */
public record ProjectScope(boolean all, Set<UUID> projectIds) {

    public ProjectScope {
        projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
    }

    /** Owner/admin scope: every project is visible. */
    public static ProjectScope unrestricted() {
        return new ProjectScope(true, Set.of());
    }

    /** Regular-member scope: only the given project ids are visible. */
    public static ProjectScope restrictedTo(Set<UUID> projectIds) {
        return new ProjectScope(false, projectIds);
    }

    /** True when the caller can see no projects at all (restricted with an empty set). */
    public boolean isEmpty() {
        return !all && projectIds.isEmpty();
    }
}
