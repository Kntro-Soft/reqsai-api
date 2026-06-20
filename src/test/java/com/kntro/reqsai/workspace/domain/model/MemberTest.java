package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: Member Aggregate")
class MemberTest {

    @Test
    @DisplayName("should create active member with user id")
    void should_create_active_member_with_user_id() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Member member = new Member(
                organizationId,
                userId,
                "member@example.com",
                "Member Name",
                OrgRole.MEMBER,
                MemberStatus.ACTIVE,
                UUID.randomUUID(),
                Instant.now());

        assertThat(member.getOrganizationId()).isEqualTo(organizationId);
        assertThat(member.getUserId()).isEqualTo(userId);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getEmail()).isEqualTo("member@example.com");
    }

    @Test
    @DisplayName("should create pending member with email and display name")
    void should_create_pending_member_with_email_and_display_name() {
        UUID invitedBy = UUID.randomUUID();
        Instant invitedAt = Instant.now();

        Member member = new Member(
                UUID.randomUUID(),
                null,
                "invitee@example.com",
                "Invitee Name",
                OrgRole.ADMIN,
                MemberStatus.PENDING,
                invitedBy,
                invitedAt);

        assertThat(member.getUserId()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(member.getInvitedBy()).isEqualTo(invitedBy);
        assertThat(member.getInvitedAt()).isEqualTo(invitedAt);
    }

    @Test
    @DisplayName("should reject active member without user id")
    void should_reject_active_member_without_user_id() {
        assertThatThrownBy(() -> new Member(
                UUID.randomUUID(),
                null,
                "member@example.com",
                "Member Name",
                OrgRole.MEMBER,
                MemberStatus.ACTIVE,
                UUID.randomUUID(),
                Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject pending member without invite metadata")
    void should_reject_pending_member_without_invite_metadata() {
        assertThatThrownBy(() -> new Member(
                UUID.randomUUID(),
                null,
                "invitee@example.com",
                "Invitee Name",
                OrgRole.MEMBER,
                MemberStatus.PENDING,
                null,
                null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should change role and lifecycle state")
    void should_change_role_and_lifecycle_state() {
        Member member = new Member(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "member@example.com",
                "Member Name",
                OrgRole.MEMBER,
                MemberStatus.ACTIVE,
                UUID.randomUUID(),
                Instant.now());

        member.changeRole(OrgRole.ADMIN);
        member.deactivate();
        member.reactivate(UUID.randomUUID());

        assertThat(member.getRole()).isEqualTo(OrgRole.ADMIN);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.isAdmin()).isTrue();
    }
}
