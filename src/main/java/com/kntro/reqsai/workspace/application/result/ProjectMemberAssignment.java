package com.kntro.reqsai.workspace.application.result;

import com.kntro.reqsai.workspace.domain.model.ProjectMember;

/**
 * A project member assignment enriched with its role's display name, so the members list is
 * self-contained: a caller who can read members ({@code MEMBER_READ}) sees each member's role
 * without also needing {@code ROLE_READ} to look the name up separately.
 *
 * @param assignment the project member assignment (member id, role id, audit fields)
 * @param roleName   the display name of the assignment's project role, or {@code null} if the role
 *                   is missing (a dangling assignment)
 */
public record ProjectMemberAssignment(ProjectMember assignment, String roleName) {
}
