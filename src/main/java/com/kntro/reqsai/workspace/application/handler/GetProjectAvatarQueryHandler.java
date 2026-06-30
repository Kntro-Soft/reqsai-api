package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.query.GetProjectAvatarQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads a project's stored avatar bytes for the public serve endpoint. Returns an empty {@link Optional}
 * when the project is unknown or has no avatar — the endpoint then responds {@code 404}.
 * <p>
 * The project lives in a tenant schema and the serve endpoint is unauthenticated (no JWT to derive the
 * tenant from), so this handler resolves the schema from the {@code organizationId} and binds the tenant
 * context (via {@link TenantContext#runWith}) around the transactional read — a coordination concern that
 * belongs in the application layer, not the controller. The read itself runs in {@link ProjectAvatarReader}
 * so its {@code @Transactional} boundary is applied and the lazy blob loads inside an open session.
 */
@Component
@RequiredArgsConstructor
public class GetProjectAvatarQueryHandler {

    private final ProjectAvatarReader reader;
    private final TenantSchemaResolver tenantSchemaResolver;

    public Optional<GeneratedAvatar> handle(GetProjectAvatarQuery query) {
        String tenantId = query.organizationId().toString();
        String schema = tenantSchemaResolver.resolveTenantSchema(tenantId);
        AtomicReference<Optional<GeneratedAvatar>> result = new AtomicReference<>(Optional.empty());
        TenantContext.runWith(
                new TenantContext.TenantSnapshot(tenantId, schema),
                () -> result.set(reader.read(query)));
        return result.get();
    }
}
