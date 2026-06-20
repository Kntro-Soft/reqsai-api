package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.command.UpdateUserPreferencesCommand;
import com.kntro.reqsai.iam.application.port.OrganizationLookupPort;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.User;
import com.kntro.reqsai.iam.domain.model.UserPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the user's navigation preferences (last visited organization / project).
 * <p>
 * When {@code lastVisitedOrgId} is provided, ownership is verified before persisting — the org must
 * belong to the requesting user. Once saved, the next {@code /auth/refresh} call will embed that
 * {@code orgId} in the new JWT, effectively switching the active organization context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateUserPreferencesCommandHandler {

    private final UserRepository users;
    private final OrganizationLookupPort organizations;

    @Transactional
    public User handle(UpdateUserPreferencesCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(command.userId()));

        if (command.lastVisitedOrgId() != null
                && !organizations.isOwnerOf(command.lastVisitedOrgId(), command.userId())) {
            throw IamExceptions.organizationNotOwned(command.lastVisitedOrgId());
        }

        UserPreferences current = user.getPreferences();
        user.updatePreferences(UserPreferences.of(
                command.lastVisitedOrgId(),
                current != null ? current.lastVisitedProjectId() : null));

        User saved = users.save(user);
        log.info("Updated preferences for user {} — lastVisitedOrgId={}", command.userId(), command.lastVisitedOrgId());
        return saved;
    }
}
