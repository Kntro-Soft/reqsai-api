package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lists the organizations owned by the authenticated user, newest first. */
@Component
@RequiredArgsConstructor
public class ListOrganizationsQueryHandler {

    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public List<Organization> handle(UUID ownerId) {
        return organizations.findAllByOwnerId(ownerId);
    }
}
