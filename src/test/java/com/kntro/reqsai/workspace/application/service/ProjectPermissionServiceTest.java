package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
                org, UUID.randomUUID(), owner, Permission.MANAGE_MEMBERS, "manage project members"))
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
                org, UUID.randomUUID(), adminUser, Permission.MANAGE_ROLES, "manage project roles"))
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
                .thenReturn(Optional.of(new ProjectRole(projectId, "Lead", Set.of(Permission.MANAGE_MEMBERS))));

        assertThatCode(() -> service().assertHasProjectPermission(
                org, projectId, memberUser, Permission.MANAGE_MEMBERS, "manage project members"))
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
                .thenReturn(Optional.of(new ProjectRole(projectId, "Reader", Set.of(Permission.READ_PROJECT))));

        assertThatThrownBy(() -> service().assertHasProjectPermission(
                org, projectId, memberUser, Permission.MANAGE_MEMBERS, "manage project members"))
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
                org, projectId, memberUser, Permission.MANAGE_ROLES, "manage project roles"))
                .isInstanceOf(DomainException.class);
    }
}
