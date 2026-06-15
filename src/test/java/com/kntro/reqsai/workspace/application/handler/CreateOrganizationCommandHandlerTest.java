package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.ProvisioningService;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.OrgStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.CreateOrganizationCommandMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CreateOrganizationCommandHandler} with mocked collaborators.
 *
 * @see CreateOrganizationCommandHandler
 */
@DisplayName("Application: Create Organization")
@ExtendWith(MockitoExtension.class)
class CreateOrganizationCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProvisioningService provisioningService;
    @InjectMocks
    private CreateOrganizationCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should derive the slug, provision the tenant and activate")
        void should_derive_slug_provision_and_activate() {
            // Arrange
            givenSlugIsFree();
            givenSaveReturnsArgument();

            // Act
            Organization org = handler.handle(CreateOrganizationCommandMother.withName("Acme Corp"));

            // Assert
            assertThat(org.getSlug().value()).isEqualTo("acme-corp");
            assertThat(org.getStatus()).isEqualTo(OrgStatus.ACTIVE);
            assertThat(org.getSettings().meetingLanguage().value()).isEqualTo("en-US");
            verify(provisioningService).provisionTenant("acme-corp");
            verify(organizations, times(2)).save(any(Organization.class)); // PENDING then ACTIVE
        }

        @Test
        @DisplayName("should honour an explicit slug")
        void should_honour_explicit_slug() {
            // Arrange
            givenSlugIsFree();
            givenSaveReturnsArgument();

            // Act
            Organization org = handler.handle(CreateOrganizationCommandMother.withSlug("custom-slug"));

            // Assert
            assertThat(org.getSlug().value()).isEqualTo("custom-slug");
            verify(provisioningService).provisionTenant("custom-slug");
        }

        @Test
        @DisplayName("should fall back to the default language when none is given")
        void should_default_language_when_omitted() {
            // Arrange
            givenSlugIsFree();
            givenSaveReturnsArgument();

            // Act
            Organization org = handler.handle(CreateOrganizationCommandMother.minimal());

            // Assert
            assertThat(org.getSettings().meetingLanguage().value()).isEqualTo("es-PE");
            assertThat(org.getStatus()).isEqualTo(OrgStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Provisioning failure")
    class ProvisioningFailure {

        @Test
        @DisplayName("should leave the org PENDING (saved once, not activated) when provisioning fails")
        void should_not_activate_when_provisioning_fails() {
            // Arrange
            givenSlugIsFree();
            givenSaveReturnsArgument();
            doThrow(new IllegalStateException("schema creation failed"))
                    .when(provisioningService).provisionTenant(any());

            // Act & Assert — the failure propagates...
            assertThatThrownBy(() -> handler.handle(CreateOrganizationCommandMother.valid()))
                    .isInstanceOf(IllegalStateException.class);

            // ...and the org was persisted exactly once (PENDING); activate()/second save never ran.
            verify(organizations, times(1)).save(any(Organization.class));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail on a duplicate slug before provisioning")
        void should_fail_on_duplicate_slug_before_provisioning() {
            // Arrange
            when(organizations.existsBySlug(any())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(CreateOrganizationCommandMother.valid()))
                    .isInstanceOf(DomainException.class);
            verify(provisioningService, never()).provisionTenant(any());
            verify(organizations, never()).save(any());
        }
    }

    // Stubs

    private void givenSlugIsFree() {
        when(organizations.existsBySlug(any())).thenReturn(false);
    }

    private void givenSaveReturnsArgument() {
        when(organizations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
