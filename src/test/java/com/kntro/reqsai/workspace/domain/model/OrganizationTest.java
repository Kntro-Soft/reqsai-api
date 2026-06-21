package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import com.kntro.reqsai.testsupport.AggregateEvents;
import com.kntro.reqsai.workspace.domain.event.OrganizationCreatedEvent;
import com.kntro.reqsai.workspace.domain.valueobjects.GenerationSettings;
import com.kntro.reqsai.workspace.mothers.OrganizationBuilder;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Organization aggregate root.
 *
 * @see Organization
 */
@DisplayName("Domain: Organization Aggregate")
class OrganizationTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create organization in PENDING status")
        void should_create_organization_in_pending_status() {
            // Act
            Organization org = OrganizationMother.pending().build();

            // Assert
            assertThat(org.getStatus()).isEqualTo(OrgStatus.PENDING);
            assertThat(org.getId()).isNotNull();
            assertThat(org.getSlug()).isNotNull();
        }

        @Test
        @DisplayName("should reject a blank name")
        void should_reject_blank_name() {
            // Act & Assert
            assertThatThrownBy(() -> OrganizationBuilder.anOrganization().withName("  ").build())
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("should expose settings as value objects")
        void should_expose_settings_as_value_objects() {
            // Arrange
            Organization org = OrganizationMother.pending()
                    .withMeetingLanguage("en-US")
                    .withAudioRetentionDays(7)
                    .build();

            // Assert
            assertThat(org.getSettings().meetingLanguage().value()).isEqualTo("en-US");
            assertThat(org.getSettings().audioRetentionDays()).isEqualTo(7);
            assertThat(org.getPlanLimits().maxProjects()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should activate and register OrganizationCreatedEvent")
        void should_activate_and_register_event() {
            // Arrange
            Organization org = OrganizationMother.pending().build();

            // Act
            org.activate();

            // Assert
            assertThat(org.getStatus()).isEqualTo(OrgStatus.ACTIVE);
            assertThat(AggregateEvents.of(org))
                    .hasSize(1)
                    .allSatisfy(e -> assertThat(e).isInstanceOf(OrganizationCreatedEvent.class));
        }

        @Test
        @DisplayName("should transition through deactivate, reactivate and delete")
        void should_transition_deactivate_reactivate_delete() {
            // Arrange
            Organization org = OrganizationMother.active().build();

            // Act & Assert
            org.deactivate();
            assertThat(org.getStatus()).isEqualTo(OrgStatus.INACTIVE);
            org.reactivate();
            assertThat(org.getStatus()).isEqualTo(OrgStatus.ACTIVE);
            org.delete();
            assertThat(org.getStatus()).isEqualTo(OrgStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("Updates")
    class Updates {

        @Test
        @DisplayName("should rename the organization")
        void should_rename_organization() {
            // Arrange
            Organization org = OrganizationMother.pending().build();

            // Act
            org.rename("Acme International");

            // Assert
            assertThat(org.getName()).isEqualTo("Acme International");
        }

        @Test
        @DisplayName("should replace generation settings")
        void should_replace_settings() {
            // Arrange
            Organization org = OrganizationMother.pending().build();

            // Act
            org.updateSettings(GenerationSettings.of(LanguageCode.of("pt-BR"), -1));

            // Assert
            assertThat(org.getSettings().meetingLanguage().value()).isEqualTo("pt-BR");
            assertThat(org.getSettings().audioRetentionDays()).isEqualTo(-1);
        }
    }
}
