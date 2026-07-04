package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: ProjectRole Aggregate")
class ProjectRoleTest {

    @Test
    @DisplayName("should create role with name and permissions")
    void should_create_role_with_name_and_permissions() {
        ProjectRole role = new ProjectRole(
                UUID.randomUUID(),
                "Analyst",
                Set.of(Permission.MEMBER_READ, Permission.DOCUMENT_READ));

        assertThat(role.getName()).isEqualTo("Analyst");
        assertThat(role.getPermissions()).containsExactlyInAnyOrder(
                Permission.MEMBER_READ,
                Permission.DOCUMENT_READ);
    }

    @Test
    @DisplayName("should reject blank name")
    void should_reject_blank_name() {
        assertThatThrownBy(() -> new ProjectRole(
                UUID.randomUUID(),
                "   ",
                Set.of(Permission.MEMBER_READ)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject empty permissions")
    void should_reject_empty_permissions() {
        assertThatThrownBy(() -> new ProjectRole(UUID.randomUUID(), "Analyst", Set.of()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should update name and permissions")
    void should_update_name_and_permissions() {
        ProjectRole role = new ProjectRole(
                UUID.randomUUID(),
                "Analyst",
                Set.of(Permission.MEMBER_READ));

        role.update("Lead Analyst", Set.of(Permission.MEMBER_READ, Permission.MEMBER_INVITE));

        assertThat(role.getName()).isEqualTo("Lead Analyst");
        assertThat(role.getPermissions()).containsExactlyInAnyOrder(
                Permission.MEMBER_READ,
                Permission.MEMBER_INVITE);
    }
}
