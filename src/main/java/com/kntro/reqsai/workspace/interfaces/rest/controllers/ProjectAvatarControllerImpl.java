package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.shared.infrastructure.avatar.AvatarResponses;
import com.kntro.reqsai.shared.infrastructure.avatar.GeneratedAvatar;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.handler.GetProjectAvatarQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetProjectAvatarQuery;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectAvatarController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the {@link ProjectAvatarController} API contract (public).
 * <p>
 * Projects live in a tenant schema, so the avatar read must run under the tenant context. The endpoint is
 * unauthenticated (no JWT to derive the tenant from), so the schema is resolved from the {@code orgId}
 * path variable and bound for the duration of the read via {@link TenantContext#runWith}.
 */
@RestController
@RequiredArgsConstructor
public class ProjectAvatarControllerImpl implements ProjectAvatarController {

    private final GetProjectAvatarQueryHandler getProjectAvatar;
    private final TenantSchemaResolver tenantSchemaResolver;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID orgId, UUID projectId) {
        String schema = tenantSchemaResolver.resolveTenantSchema(orgId.toString());
        AtomicReference<Optional<GeneratedAvatar>> result = new AtomicReference<>(Optional.empty());
        TenantContext.runWith(
                new TenantContext.TenantSnapshot(orgId.toString(), schema),
                () -> result.set(getProjectAvatar.handle(new GetProjectAvatarQuery(orgId, projectId))));
        return AvatarResponses.of(result.get());
    }
}
