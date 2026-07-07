package com.kntro.reqsai.shared.application.avatar;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;

import java.util.UUID;

/**
 * Builds the API paths of the public avatar serve endpoints. Lives in the application layer so the
 * interfaces-layer response mappers can compute {@code avatarUrl} without importing
 * {@code ..infrastructure..} (the {@link ApiVersioning} base is referenced here, one layer down).
 */
public final class AvatarPaths {

    private AvatarPaths() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static String organization(UUID organizationId) {
        return ApiVersioning.BASE + "/organizations/" + organizationId + "/avatar";
    }

    public static String project(UUID organizationId, UUID projectId) {
        return ApiVersioning.BASE + "/organizations/" + organizationId + "/projects/" + projectId + "/avatar";
    }

    public static String user(UUID userId) {
        return ApiVersioning.BASE + "/users/" + userId + "/avatar";
    }
}
