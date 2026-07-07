package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: ProjectMember Aggregate")
class ProjectMemberTest {

    @Test
    @DisplayName("should create valid assignment")
    void should_create_valid_assignment() {
        UUID roleId = UUID.randomUUID();

        ProjectMember assignment = new ProjectMember(
                UUID.randomUUID(),
                UUID.randomUUID(),
                roleId,
                UUID.randomUUID(),
                Instant.now());

        assertThat(assignment.getRoleId()).isEqualTo(roleId);
        assertThat(assignment.getAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("should change role")
    void should_change_role() {
        ProjectMember assignment = new ProjectMember(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now());

        UUID newRoleId = UUID.randomUUID();
        assignment.changeRole(newRoleId);

        assertThat(assignment.getRoleId()).isEqualTo(newRoleId);
    }

    @Test
    @DisplayName("should reject null references")
    void should_reject_null_references() {
        assertThatThrownBy(() -> new ProjectMember(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now()))
                .isInstanceOf(DomainException.class);
    }
}
