package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.domain.model.BasePermission;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@DisplayName("Service: Project Access")
@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private ProjectMemberRepository assignments;
    @Mock
    private MemberRepository orgMembers;

    private ProjectAccessService service(Organization org, UUID owner) {
        OrganizationAdminAccessService orgAccess = new OrganizationAdminAccessService(orgMembers);
        return new ProjectAccessService(members, assignments, orgAccess);
    }

    private Member member(UUID orgId, UUID userId, OrgRole role) {
        return new Member(orgId, userId, "u" + UUID.randomUUID() + "@example.com", "User", role,
                MemberStatus.ACTIVE, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("owner has unrestricted access (empty optional)")
    void owner_unrestricted() {
        Organization org = OrganizationMother.active().build();
        UUID owner = org.getOwnerId();
        ProjectAccessService service = service(org, owner);

        Optional<Set<UUID>> result = service.accessibleProjectIds(org, owner);

        assertThat(result).isEmpty();
        assertThatCode(() -> service.assertCanAccessProject(org, UUID.randomUUID(), owner)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("admin has unrestricted access (empty optional)")
    void admin_unrestricted() {
        Organization org = OrganizationMother.active().build();
        UUID adminUser = UUID.randomUUID();
        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), adminUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member(org.getId(), adminUser, OrgRole.ADMIN)));
        ProjectAccessService service = service(org, org.getOwnerId());

        assertThat(service.accessibleProjectIds(org, adminUser)).isEmpty();
    }

    @Test
    @DisplayName("member sees only assigned projects")
    void member_sees_assigned_only() {
        Organization org = OrganizationMother.active().build();
        // Pin the base floor to NONE so access is driven purely by explicit assignments.
        org.changeMemberBasePermission(BasePermission.NONE);
        UUID memberUser = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);
        UUID assignedProject = UUID.randomUUID();

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(assignments.findAllByMemberId(m.getId()))
                .thenReturn(List.of(new ProjectMember(assignedProject, m.getId(), UUID.randomUUID(), UUID.randomUUID(), Instant.now())));
        ProjectAccessService service = service(org, org.getOwnerId());

        assertThat(service.accessibleProjectIds(org, memberUser)).contains(Set.of(assignedProject));
        assertThatCode(() -> service.assertCanAccessProject(org, assignedProject, memberUser)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("member is denied an unassigned project")
    void member_denied_unassigned() {
        Organization org = OrganizationMother.active().build();
        // Pin the base floor to NONE so access is driven purely by explicit assignments.
        org.changeMemberBasePermission(BasePermission.NONE);
        UUID memberUser = UUID.randomUUID();
        Member m = member(org.getId(), memberUser, OrgRole.MEMBER);
        UUID assignedProject = UUID.randomUUID();
        UUID otherProject = UUID.randomUUID();

        when(orgMembers.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
        when(assignments.findAllByMemberId(m.getId()))
                .thenReturn(List.of(new ProjectMember(assignedProject, m.getId(), UUID.randomUUID(), UUID.randomUUID(), Instant.now())));
        ProjectAccessService service = service(org, org.getOwnerId());

        assertThatThrownBy(() -> service.assertCanAccessProject(org, otherProject, memberUser))
                .isInstanceOf(DomainException.class);
    }
}
