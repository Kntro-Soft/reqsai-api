package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.UpdateOrganizationCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Update Organization")
@ExtendWith(MockitoExtension.class)
class UpdateOrganizationCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @InjectMocks
    private UpdateOrganizationCommandHandler handler;

    @Nested
    @DisplayName("Successful update")
    class SuccessfulUpdate {

        @Test
        @DisplayName("should update organization name and settings when requested by owner")
        void should_update_organization_name_and_settings_when_requested_by_owner() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active()
                    .withOwnerId(ownerId)
                    .withMeetingLanguage("en-US")
                    .withAudioRetentionDays(30)
                    .build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    "Acme International",
                    "pt-BR",
                    -1,
                    ownerId
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(organizations.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Organization updated = handler.handle(command);

            assertThat(updated.getName()).isEqualTo("Acme International");
            assertThat(updated.getSettings().meetingLanguage().value()).isEqualTo("pt-BR");
            assertThat(updated.getSettings().audioRetentionDays()).isEqualTo(-1);
            verify(organizations).save(organization);
        }

        @Test
        @DisplayName("should update only the name and leave settings unchanged when other fields are null")
        void should_update_only_name_and_leave_settings_unchanged() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active()
                    .withOwnerId(ownerId)
                    .withName("Acme")
                    .withMeetingLanguage("en-US")
                    .withAudioRetentionDays(30)
                    .build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    "Acme International",
                    null,
                    null,
                    ownerId
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(organizations.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Organization updated = handler.handle(command);

            assertThat(updated.getName()).isEqualTo("Acme International");
            assertThat(updated.getSettings().meetingLanguage().value()).isEqualTo("en-US");
            assertThat(updated.getSettings().audioRetentionDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("should update only audio retention and leave name and language unchanged")
        void should_update_only_audio_retention() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active()
                    .withOwnerId(ownerId)
                    .withName("Acme")
                    .withMeetingLanguage("en-US")
                    .withAudioRetentionDays(30)
                    .build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    null,
                    null,
                    -1,
                    ownerId
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(organizations.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Organization updated = handler.handle(command);

            assertThat(updated.getName()).isEqualTo("Acme");
            assertThat(updated.getSettings().meetingLanguage().value()).isEqualTo("en-US");
            assertThat(updated.getSettings().audioRetentionDays()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should be a no-op that leaves everything unchanged when all fields are null")
        void should_be_a_no_op_when_all_fields_are_null() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active()
                    .withOwnerId(ownerId)
                    .withName("Acme")
                    .withMeetingLanguage("en-US")
                    .withAudioRetentionDays(30)
                    .build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    null,
                    null,
                    null,
                    ownerId
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
            when(organizations.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Organization updated = handler.handle(command);

            assertThat(updated.getName()).isEqualTo("Acme");
            assertThat(updated.getSettings().meetingLanguage().value()).isEqualTo("en-US");
            assertThat(updated.getSettings().audioRetentionDays()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    orgId,
                    "Acme International",
                    "pt-BR",
                    30,
                    UUID.randomUUID()
            );

            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(organizations, never()).save(any());
        }

        @Test
        @DisplayName("should fail if requested by non-owner")
        void should_fail_if_requested_by_non_owner() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active().withOwnerId(ownerId).build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    "Acme International",
                    "pt-BR",
                    30,
                    UUID.randomUUID()
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(organizations, never()).save(any());
        }

        @Test
        @DisplayName("should fail on invalid meeting language")
        void should_fail_on_invalid_meeting_language() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active().withOwnerId(ownerId).build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    "Acme International",
                    "portuguese",
                    30,
                    ownerId
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(organizations, never()).save(any());
        }

        @Test
        @DisplayName("should fail on invalid audio retention days")
        void should_fail_on_invalid_audio_retention_days() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active().withOwnerId(ownerId).build();

            UpdateOrganizationCommand command = new UpdateOrganizationCommand(
                    organization.getId(),
                    "Acme International",
                    "pt-BR",
                    -2,
                    ownerId
            );

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(organizations, never()).save(any());
        }
    }
}
