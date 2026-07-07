package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.ListMembersQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListMembersQueryHandler {

    private final OrganizationRepository organizations;
    private final MemberRepository members;
    private final AccountLookupPort accountLookup;

    @Transactional(readOnly = true)
    public List<Member> handle(ListMembersQuery query) {
        Organization organization = organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        UUID ownerId = organization.getOwnerId();
        List<Member> roster = members.findAllByOrganizationIdAndStatusIn(query.organizationId(),
                        List.of(MemberStatus.ACTIVE, MemberStatus.PENDING)).stream()
                .filter(member -> !ownerId.equals(member.getUserId()))
                .toList();

        return ownerMember(organization, ownerId)
                .map(owner -> {
                    List<Member> withOwner = new ArrayList<>(roster.size() + 1);
                    withOwner.add(owner);
                    withOwner.addAll(roster);
                    return List.copyOf(withOwner);
                })
                .orElseGet(() -> List.copyOf(roster));
    }

    /** Synthesizes the organization owner as an ACTIVE OWNER row from their IAM profile (email + name). */
    private Optional<Member> ownerMember(Organization organization, UUID ownerId) {
        return accountLookup.findProfileByUserId(ownerId)
                .map(profile -> new Member(
                        organization.getId(),
                        ownerId,
                        profile.email(),
                        profile.displayName(),
                        OrgRole.OWNER,
                        MemberStatus.ACTIVE,
                        null,
                        null));
    }
}
