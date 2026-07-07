package com.kntro.reqsai.iam.interfaces.rest.mappers.response;

import com.kntro.reqsai.iam.application.result.AuthenticatedSession;
import com.kntro.reqsai.iam.application.result.RefreshedSession;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.AuthResponse;

/**
 * Maps {@link AuthenticatedSession} and {@link RefreshedSession} to the {@link AuthResponse} DTO.
 * Neither the raw refresh token nor the user object appear in the response body for refresh operations.
 */
public final class AuthResponseMapper {

    private static final String TOKEN_TYPE = "Bearer";

    private AuthResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Maps a sign-in session (includes user profile) to {@link AuthResponse}.
     * The raw refresh token is intentionally omitted from the DTO — it is set as a cookie by the controller.
     */
    public static AuthResponse toResponse(AuthenticatedSession session) {
        return new AuthResponse(
                session.accessToken(),
                TOKEN_TYPE,
                session.expiresInSeconds(),
                UserResponseMapper.toResponse(session.user(), session.email()),
                session.organizationId());
    }

    /**
     * Maps a token-refresh result to {@link AuthResponse} (no user payload — caller already has it).
     * The new raw refresh token is omitted from the DTO — it is set as a cookie by the controller.
     */
    public static AuthResponse toResponse(RefreshedSession session) {
        return new AuthResponse(
                session.accessToken(),
                TOKEN_TYPE,
                session.expiresInSeconds(),
                null,
                session.organizationId());
    }
}
