package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.ChangeMemberStatusCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Change Member Status")
@ExtendWith(MockitoExtension.class)
class ChangeMemberStatusCommandHandlerTest {

    private static final List<MemberStatus> MANAGEABLE = List.of(MemberStatus.ACTIVE, MemberStatus.INACTIVE);

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @InjectMocks
    private OrganizationAdminAccessService access;

    private ChangeMemberStatusCommandHandler handler;

    private void initHandler() {
        handler = new ChangeMemberStatusCommandHandler(organizations, members, access);
    }

    private Member member(UUID orgId, UUID userId, OrgRole role, MemberStatus status) {
        return new Member(orgId, userId, "u" + UUID.randomUUID() + "@example.com", "User", role,
                status, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("owner deactivates an active member")
    void owner_deactivates_member() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        UUID owner = org.getOwnerId();
        Member target = member(orgId, UUID.randomUUID(), OrgRole.MEMBER, MemberStatus.ACTIVE);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MANAGEABLE)).thenReturn(Optional.of(target));
        when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
        initHandler();

        Member result = handler.handle(new ChangeMemberStatusCommand(orgId, target.getId(), MemberStatus.INACTIVE, owner));

        assertThat(result.getStatus()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("owner reactivates an inactive member")
    void owner_reactivates_member() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        UUID owner = org.getOwnerId();
        Member target = member(orgId, UUID.randomUUID(), OrgRole.MEMBER, MemberStatus.INACTIVE);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MANAGEABLE)).thenReturn(Optional.of(target));
        when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
        initHandler();

        Member result = handler.handle(new ChangeMemberStatusCommand(orgId, target.getId(), MemberStatus.ACTIVE, owner));

        assertThat(result.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("admin cannot change another admin's status")
    void admin_cannot_change_other_admin() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        UUID adminUser = UUID.randomUUID();
        Member adminMember = member(orgId, adminUser, OrgRole.ADMIN, MemberStatus.ACTIVE);
        Member otherAdmin = member(orgId, UUID.randomUUID(), OrgRole.ADMIN, MemberStatus.ACTIVE);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByOrganizationIdAndUserIdAndStatus(orgId, adminUser, MemberStatus.ACTIVE)).thenReturn(Optional.of(adminMember));
        when(members.findByIdAndOrganizationIdAndStatusIn(otherAdmin.getId(), orgId, MANAGEABLE)).thenReturn(Optional.of(otherAdmin));
        initHandler();

        assertThatThrownBy(() -> handler.handle(new ChangeMemberStatusCommand(orgId, otherAdmin.getId(), MemberStatus.INACTIVE, adminUser)))
                .isInstanceOf(DomainException.class);
        verify(members, never()).save(any());
    }

    @Test
    @DisplayName("cannot change the OWNER member status")
    void cannot_change_owner_member() {
        Organization org = OrganizationMother.active().build();
        UUID orgId = org.getId();
        UUID owner = org.getOwnerId();
        Member ownerMember = member(orgId, UUID.randomUUID(), OrgRole.OWNER, MemberStatus.ACTIVE);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByIdAndOrganizationIdAndStatusIn(ownerMember.getId(), orgId, MANAGEABLE)).thenReturn(Optional.of(ownerMember));
        initHandler();

        assertThatThrownBy(() -> handler.handle(new ChangeMemberStatusCommand(orgId, ownerMember.getId(), MemberStatus.INACTIVE, owner)))
                .isInstanceOf(DomainException.class);
        verify(members, never()).save(any());
    }
}
