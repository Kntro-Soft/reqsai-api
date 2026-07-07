package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.ListOrganizationsQuery;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lists every organization a user can access: those they own plus those they are an active member of.
 * Both the org registry and the membership table now live in the shared schema, so this is a couple of
 * indexed reads. Owned organizations come first; duplicates are removed.
 */
@Component
@RequiredArgsConstructor
public class ListOrganizationsQueryHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public List<Organization> handle(ListOrganizationsQuery query) {
        UUID userId = query.requestedBy();

        Map<UUID, Organization> byId = new LinkedHashMap<>();
        for (Organization org : organizations.findAllByOwnerId(userId)) {
            byId.put(org.getId(), org);
        }

        List<UUID> memberOrgIds = members.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .map(Member::getOrganizationId)
                .toList();
        for (Organization org : organizations.findAllByIdIn(memberOrgIds)) {
            byId.putIfAbsent(org.getId(), org);
        }

        return new ArrayList<>(byId.values());
    }
}
