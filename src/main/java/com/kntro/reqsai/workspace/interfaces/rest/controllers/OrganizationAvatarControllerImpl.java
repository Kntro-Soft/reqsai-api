package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.shared.infrastructure.avatar.AvatarResponses;
import com.kntro.reqsai.workspace.application.handler.GetOrganizationAvatarQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetOrganizationAvatarQuery;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.OrganizationAvatarController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link OrganizationAvatarController} API contract (public). */
@RestController
@RequiredArgsConstructor
public class OrganizationAvatarControllerImpl implements OrganizationAvatarController {

    private final GetOrganizationAvatarQueryHandler getOrganizationAvatar;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID orgId) {
        return AvatarResponses.of(getOrganizationAvatar.handle(new GetOrganizationAvatarQuery(orgId)));
    }
}
