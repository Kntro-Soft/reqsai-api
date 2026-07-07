package com.kntro.reqsai.discovery.interfaces.rest.security;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SpEL-facing authorization facade for the discovery REST endpoints whose routes are
 * <em>session-scoped</em> ({@code /api/sessions/{sessionId}/...}) and therefore carry neither an
 * {@code orgId} nor a {@code projectId} path variable. Referenced from {@code @PreAuthorize} as
 * {@code @discoveryAuthz}; project-scoped discovery routes use the workspace facade
 * ({@code @authz.projectPermission(#projectId, '...', authentication)}) directly.
 * <p>
 * The session is resolved to its project here (tenant-scoped repository — the JWT filter has
 * already bound the schema), then the check delegates to {@link WorkspaceModuleApi}, the single
 * source of truth for project permissions: org owners/admins bypass, regular members need a
 * project role carrying the permission.
 * <p>
 * When the session does not exist the gate passes so the handler answers 404 rather than masking
 * it as a 403 (same convention as the workspace facade with absent organizations).
 */
@Component("discoveryAuthz")
@RequiredArgsConstructor
public class DiscoveryAuthorization {

    private final DiscoverySessionRepository sessions;
    private final WorkspaceModuleApi workspace;

    /** Caller holds the named permission on the project owning the given session. */
    public boolean sessionPermission(UUID sessionId, String permission, Authentication authentication) {
        UUID userId = callerId(authentication);
        if (userId == null) {
            return false;
        }
        // Absent session → let the handler produce a 404 instead of masking it as a 403.
        return sessions.findById(sessionId)
                .map(session -> workspace.callerHasProjectPermission(session.getProjectId(), userId, permission))
                .orElse(true);
    }

    private UUID callerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
