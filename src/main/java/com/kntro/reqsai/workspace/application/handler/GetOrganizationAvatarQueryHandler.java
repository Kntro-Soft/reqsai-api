package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.infrastructure.avatar.GeneratedAvatar;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.GetOrganizationAvatarQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Loads an organization's stored avatar bytes for the public serve endpoint. Returns an empty
 * {@link Optional} when the organization is unknown or has no avatar — the endpoint then responds {@code 404}.
 */
@Component
@RequiredArgsConstructor
public class GetOrganizationAvatarQueryHandler {

    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Optional<GeneratedAvatar> handle(GetOrganizationAvatarQuery query) {
        return organizations.findById(query.organizationId())
                .filter(organization -> organization.getAvatar() != null)
                .map(organization -> new GeneratedAvatar(organization.getAvatar(), organization.getAvatarContentType()));
    }
}
