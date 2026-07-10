package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.model.BasePermission;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@DisplayName("Service: Project Permission")
@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private ProjectMemberRepository assignments;
    @Mock
    private ProjectRoleRepository roles;
    @Mock
    private MemberRepository orgMembers;

    private ProjectPermissionService service() {
        OrganizationAdminAccessService orgAccess = new OrganizationAdminAccessService(orgMembers);
        return new ProjectPermissionService(members, assignments, roles, orgAccess);
    }

    private Member member(UUID orgId, UUID userId, OrgRole role) {
        return new Member(orgId, userId, "u" + UUID.randomUUID() + "@example.com", "User", role,
                MemberStatus.ACTIVE, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("org owner passes any project permission check")
    void owner_passes() {
        Organization org = OrganizationMother.active().build();
        UUID owner = org.getOwnerId();

        assertThatCode(() -> service().assertHasProjectPermission(
                org, UUID.randomUUID(), owner, Permission.MEMBER_INVITE, "manage project members"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("org admin passes any project permission check")
    void admin_passes() {
        Organization org = OrganizationMother.active().build();
        UUID adminUser = UUID.randomUUID();
        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), adminUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member(org.getId(), adminUser, OrgRole.ADMIN)));

        assertThatCode(() -> service().assertHasProjectPermission(
                org, UUID.randomUUID(), adminUser, Permission.ROLE_CREATE, "manage project roles"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("project member with the permission passes")
    void member_with_permission_passes() {
        Organization org = OrganizationMother.active().build();
        UUID memberUser = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(assignments.findAllByMemberId(m.getId()))
                .thenReturn(List.of(new ProjectMember(projectId, m.getId(), roleId, UUID.randomUUID(), Instant.now())));
        when(roles.findByIdAndProjectId(roleId, projectId))
                .thenReturn(Optional.of(new ProjectRole(projectId, "Lead", Set.of(Permission.MEMBER_INVITE))));

        assertThatCode(() -> service().assertHasProjectPermission(
                org, projectId, memberUser, Permission.MEMBER_INVITE, "manage project members"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("project member without the permission is denied")
    void member_without_permission_denied() {
        Organization org = OrganizationMother.active().build();
        UUID memberUser = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(assignments.findAllByMemberId(m.getId()))
                .thenReturn(List.of(new ProjectMember(projectId, m.getId(), roleId, UUID.randomUUID(), Instant.now())));
        when(roles.findByIdAndProjectId(roleId, projectId))
                .thenReturn(Optional.of(new ProjectRole(projectId, "Reader", Set.of(Permission.MEMBER_READ))));

        assertThatThrownBy(() -> service().assertHasProjectPermission(
                org, projectId, memberUser, Permission.MEMBER_INVITE, "manage project members"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("member without any assignment on the project is denied")
    void member_without_assignment_denied() {
        Organization org = OrganizationMother.active().build();
        UUID memberUser = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(assignments.findAllByMemberId(m.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service().assertHasProjectPermission(
                org, projectId, memberUser, Permission.ROLE_CREATE, "manage project roles"))
                .isInstanceOf(DomainException.class);
    }

    // --- Member base permission floor -------------------------------------------------------------

    @Test
    @DisplayName("base READ lets a role-less active member READ but not WRITE")
    void base_read_grants_read_only_to_role_less_member() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.READ).build();
        UUID memberUser = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);

        lenient().when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        lenient().when(assignments.findAllByMemberId(m.getId())).thenReturn(List.of());

        assertThat(service().hasPermission(org, projectId, memberUser, Permission.STORY_READ)).isTrue();
        assertThat(service().hasPermission(org, projectId, memberUser, Permission.STORY_WRITE)).isFalse();
    }

    @Test
    @DisplayName("base NONE denies even READ to a role-less member")
    void base_none_denies_read_to_role_less_member() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.NONE).build();
        UUID memberUser = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);

        lenient().when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        lenient().when(assignments.findAllByMemberId(m.getId())).thenReturn(List.of());

        assertThat(service().hasPermission(org, projectId, memberUser, Permission.STORY_READ)).isFalse();
    }

    @Test
    @DisplayName("base READ does not grant a non-member the floor")
    void base_read_denies_non_member() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.READ).build();
        UUID stranger = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), stranger, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), stranger, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service().hasPermission(org, projectId, stranger, Permission.STORY_READ)).isFalse();
    }

    @Test
    @DisplayName("owner passes any permission regardless of base NONE")
    void owner_unaffected_by_base_none() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.NONE).build();

        assertThat(service().hasPermission(org, UUID.randomUUID(), org.getOwnerId(), Permission.STORY_WRITE)).isTrue();
    }

    // --- effectivePermissions ---------------------------------------------------------------------

    @Test
    @DisplayName("effectivePermissions returns the full catalog for an owner")
    void effective_permissions_owner_gets_all() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.NONE).build();

        assertThat(service().effectivePermissions(org, UUID.randomUUID(), org.getOwnerId()))
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    @DisplayName("effectivePermissions unions the base floor with the project role")
    void effective_permissions_union_base_and_role() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.READ).build();
        UUID memberUser = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);

        lenient().when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(assignments.findAllByMemberId(m.getId()))
                .thenReturn(List.of(new ProjectMember(projectId, m.getId(), roleId, UUID.randomUUID(), Instant.now())));
        when(roles.findByIdAndProjectId(roleId, projectId))
                .thenReturn(Optional.of(new ProjectRole(projectId, "Writer", Set.of(Permission.STORY_WRITE))));

        assertThat(service().effectivePermissions(org, projectId, memberUser))
                .contains(Permission.STORY_WRITE)                 // from the project role
                .contains(Permission.STORY_READ, Permission.MEMBER_READ) // from the READ floor
                .doesNotContain(Permission.DOCUMENT_CREATE);
    }

    @Test
    @DisplayName("effectivePermissions is empty for a non-member")
    void effective_permissions_non_member_empty() {
        Organization org = OrganizationMother.active().withMemberBasePermission(BasePermission.READ).build();
        UUID stranger = UUID.randomUUID();

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), stranger, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), stranger, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service().effectivePermissions(org, UUID.randomUUID(), stranger)).isEmpty();
    }
}
