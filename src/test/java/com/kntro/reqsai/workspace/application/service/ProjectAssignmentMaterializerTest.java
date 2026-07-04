package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRoleRepository;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Project Assignment Materializer")
@ExtendWith(MockitoExtension.class)
class ProjectAssignmentMaterializerTest {

    @Mock
    private ProjectRoleRepository projectRoles;
    @Mock
    private ProjectMemberRepository projectMembers;
    @InjectMocks
    private ProjectAssignmentMaterializer materializer;

    @Test
    @DisplayName("role present and no existing assignment -> creates a ProjectMember")
    void assigns_when_role_present() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ProjectRole role = new ProjectRole(projectId, "Analyst", Set.of(Permission.MEMBER_READ));

        when(projectRoles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.of(role));
        when(projectMembers.existsByProjectIdAndMemberId(projectId, memberId)).thenReturn(false);

        materializer.assign(projectId, roleId, memberId);

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMembers).save(captor.capture());
        ProjectMember saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getMemberId()).isEqualTo(memberId);
        assertThat(saved.getRoleId()).isEqualTo(role.getId());
    }

    @Test
    @DisplayName("role deleted since the invite -> skips assignment gracefully")
    void skips_when_role_deleted() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(projectRoles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.empty());

        materializer.assign(projectId, roleId, memberId);

        verify(projectMembers, never()).save(any());
    }

    @Test
    @DisplayName("assignment already exists -> idempotent, no new ProjectMember")
    void idempotent_when_assignment_exists() {
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ProjectRole role = new ProjectRole(projectId, "Analyst", Set.of(Permission.MEMBER_READ));

        when(projectRoles.findByIdAndProjectId(roleId, projectId)).thenReturn(Optional.of(role));
        when(projectMembers.existsByProjectIdAndMemberId(projectId, memberId)).thenReturn(true);

        materializer.assign(projectId, roleId, memberId);

        verify(projectMembers, never()).save(any());
    }

    @Test
    @DisplayName("does not depend on an event publisher -> the accept path never emits the direct-assignment notification")
    void materializer_holds_no_event_publisher() {
        boolean hasPublisher = false;
        for (Field field : ProjectAssignmentMaterializer.class.getDeclaredFields()) {
            if (ApplicationEventPublisher.class.isAssignableFrom(field.getType())) {
                hasPublisher = true;
            }
        }
        assertThat(hasPublisher)
                .as("ProjectAssignmentMaterializer must not publish ProjectMemberAssignedEvent; only the "
                        + "direct-assignment handler does, so accepted project invitations get no duplicate email")
                .isFalse();
    }
}
