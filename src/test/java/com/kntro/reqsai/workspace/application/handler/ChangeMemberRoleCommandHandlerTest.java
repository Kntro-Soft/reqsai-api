package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.ChangeMemberRoleCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * Verifies the RBAC matrix for changing an organization member's role. The
 * {@link OrganizationAdminAccessService} is a real instance backed by the mocked
 * {@link MemberRepository}, so the caller's effective role is resolved exactly as in production.
 */
@DisplayName("Application: Change Member Role")
@ExtendWith(MockitoExtension.class)
class ChangeMemberRoleCommandHandlerTest {

    private static final List<MemberStatus> MUTABLE = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @InjectMocks
    private OrganizationAdminAccessService access;

    private ChangeMemberRoleCommandHandler handler;

    private Member member(UUID orgId, UUID userId, OrgRole role) {
        return new Member(orgId, userId, "u" + UUID.randomUUID() + "@example.com", "User", role,
                MemberStatus.ACTIVE, UUID.randomUUID(), Instant.now());
    }

    private void initHandler() {
        handler = new ChangeMemberRoleCommandHandler(organizations, members, access);
    }

    private void stubCallerActiveMember(UUID orgId, UUID callerUserId, Member callerMember) {
        when(members.findByOrganizationIdAndUserIdAndStatus(orgId, callerUserId, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(callerMember));
    }

    @Nested
    @DisplayName("Owner caller")
    class OwnerCaller {

        @Test
        @DisplayName("owner promotes MEMBER to ADMIN")
        void owner_promotes_member_to_admin() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID owner = org.getOwnerId();
            Member target = member(orgId, UUID.randomUUID(), OrgRole.MEMBER);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MUTABLE)).thenReturn(Optional.of(target));
            when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
            initHandler();

            Member result = handler.handle(new ChangeMemberRoleCommand(orgId, target.getId(), OrgRole.ADMIN, owner));

            assertThat(result.getRole()).isEqualTo(OrgRole.ADMIN);
            verify(members).save(target);
        }

        @Test
        @DisplayName("owner demotes ADMIN to MEMBER")
        void owner_demotes_admin_to_member() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID owner = org.getOwnerId();
            Member target = member(orgId, UUID.randomUUID(), OrgRole.ADMIN);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MUTABLE)).thenReturn(Optional.of(target));
            when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
            initHandler();

            Member result = handler.handle(new ChangeMemberRoleCommand(orgId, target.getId(), OrgRole.MEMBER, owner));

            assertThat(result.getRole()).isEqualTo(OrgRole.MEMBER);
        }

        @Test
        @DisplayName("owner cannot change the OWNER member role")
        void owner_cannot_change_owner_member() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID owner = org.getOwnerId();
            Member target = member(orgId, UUID.randomUUID(), OrgRole.OWNER);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MUTABLE)).thenReturn(Optional.of(target));
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, target.getId(), OrgRole.ADMIN, owner)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }

        @Test
        @DisplayName("setting the OWNER role is always rejected")
        void setting_owner_role_is_rejected() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID owner = org.getOwnerId();
            UUID targetId = UUID.randomUUID();

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, targetId, OrgRole.OWNER, owner)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Admin caller")
    class AdminCaller {

        @Test
        @DisplayName("admin changes a MEMBER role")
        void admin_changes_member() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID adminUser = UUID.randomUUID();
            Member adminMember = member(orgId, adminUser, OrgRole.ADMIN);
            Member target = member(orgId, UUID.randomUUID(), OrgRole.MEMBER);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            stubCallerActiveMember(orgId, adminUser, adminMember);
            when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MUTABLE)).thenReturn(Optional.of(target));
            when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
            initHandler();

            Member result = handler.handle(new ChangeMemberRoleCommand(orgId, target.getId(), OrgRole.ADMIN, adminUser));

            assertThat(result.getRole()).isEqualTo(OrgRole.ADMIN);
        }

        @Test
        @DisplayName("admin cannot change their own role")
        void admin_cannot_change_self() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID adminUser = UUID.randomUUID();
            Member adminMember = member(orgId, adminUser, OrgRole.ADMIN);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            stubCallerActiveMember(orgId, adminUser, adminMember);
            when(members.findByIdAndOrganizationIdAndStatusIn(adminMember.getId(), orgId, MUTABLE)).thenReturn(Optional.of(adminMember));
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, adminMember.getId(), OrgRole.MEMBER, adminUser)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }

        @Test
        @DisplayName("admin cannot change another ADMIN's role")
        void admin_cannot_change_other_admin() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID adminUser = UUID.randomUUID();
            Member adminMember = member(orgId, adminUser, OrgRole.ADMIN);
            Member otherAdmin = member(orgId, UUID.randomUUID(), OrgRole.ADMIN);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            stubCallerActiveMember(orgId, adminUser, adminMember);
            when(members.findByIdAndOrganizationIdAndStatusIn(otherAdmin.getId(), orgId, MUTABLE)).thenReturn(Optional.of(otherAdmin));
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, otherAdmin.getId(), OrgRole.MEMBER, adminUser)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }

        @Test
        @DisplayName("admin cannot change the OWNER member role")
        void admin_cannot_change_owner() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID adminUser = UUID.randomUUID();
            Member adminMember = member(orgId, adminUser, OrgRole.ADMIN);
            Member ownerMember = member(orgId, UUID.randomUUID(), OrgRole.OWNER);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            stubCallerActiveMember(orgId, adminUser, adminMember);
            when(members.findByIdAndOrganizationIdAndStatusIn(ownerMember.getId(), orgId, MUTABLE)).thenReturn(Optional.of(ownerMember));
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, ownerMember.getId(), OrgRole.MEMBER, adminUser)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Member caller")
    class MemberCaller {

        @Test
        @DisplayName("regular member cannot change any role")
        void member_cannot_change_any_role() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID memberUser = UUID.randomUUID();
            Member callerMember = member(orgId, memberUser, OrgRole.MEMBER);
            Member target = member(orgId, UUID.randomUUID(), OrgRole.MEMBER);

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            stubCallerActiveMember(orgId, memberUser, callerMember);
            when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, MUTABLE)).thenReturn(Optional.of(target));
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, target.getId(), OrgRole.ADMIN, memberUser)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }

        @Test
        @DisplayName("non-member caller is rejected")
        void non_member_caller_rejected() {
            Organization org = OrganizationMother.active().build();
            UUID orgId = org.getId();
            UUID stranger = UUID.randomUUID();

            when(organizations.findById(orgId)).thenReturn(Optional.of(org));
            when(members.findByOrganizationIdAndUserIdAndStatus(orgId, stranger, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
            initHandler();

            assertThatThrownBy(() -> handler.handle(new ChangeMemberRoleCommand(orgId, UUID.randomUUID(), OrgRole.ADMIN, stranger)))
                    .isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }
    }
}
