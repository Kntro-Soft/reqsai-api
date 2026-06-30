package com.kntro.reqsai.iam.interfaces.rest.mappers.response;

import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.domain.model.UserPreferences;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserPreferencesResponse;
import com.kntro.reqsai.iam.interfaces.rest.dto.response.UserResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;

/** Maps the {@link User} aggregate to its response DTO. */
public final class UserResponseMapper {

    private UserResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                ApiVersioning.BASE + "/users/" + user.getId() + "/avatar",
                toPreferencesResponse(user.getPreferences()));
    }

    private static UserPreferencesResponse toPreferencesResponse(UserPreferences prefs) {
        if (prefs == null) return new UserPreferencesResponse(null, null);
        return new UserPreferencesResponse(prefs.lastVisitedOrgId(), prefs.lastVisitedProjectId());
    }
}
