package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import com.kntro.reqsai.shared.interfaces.rest.AvatarResponses;
import com.kntro.reqsai.shared.interfaces.rest.AvatarUploads;
import com.kntro.reqsai.workspace.application.command.UpdateOrganizationAvatarCommand;
import com.kntro.reqsai.workspace.application.handler.GetOrganizationAvatarQueryHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateOrganizationAvatarCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetOrganizationAvatarQuery;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.OrganizationResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.OrganizationAvatarController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Implementation of the {@link OrganizationAvatarController} API contract (public GET, authenticated PUT). */
@RestController
@RequiredArgsConstructor
public class OrganizationAvatarControllerImpl implements OrganizationAvatarController {

    private final GetOrganizationAvatarQueryHandler getOrganizationAvatar;
    private final UpdateOrganizationAvatarCommandHandler updateOrganizationAvatar;

    @Override
    public ResponseEntity<byte[]> getAvatar(UUID orgId) {
        return AvatarResponses.of(getOrganizationAvatar.handle(new GetOrganizationAvatarQuery(orgId)));
    }

    @Override
    public ResponseEntity<OrganizationResponse> uploadAvatar(UUID orgId, MultipartFile file, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        GeneratedAvatar avatar = AvatarUploads.validated(file);
        Organization organization = updateOrganizationAvatar.handle(
                new UpdateOrganizationAvatarCommand(orgId, requestedBy, avatar.bytes(), avatar.contentType()));
        return ResponseEntity.ok(OrganizationResponseMapper.toResponse(organization));
    }
}
