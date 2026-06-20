package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.AggregateEvents;
import com.kntro.reqsai.workspace.domain.event.ProjectCreatedEvent;
import com.kntro.reqsai.workspace.mothers.ProjectBuilder;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: Project Aggregate")
class ProjectTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create project in ACTIVE status and raise event")
        void should_create_project_in_active_status_and_raise_event() {
            // Arrange
            UUID orgId = UUID.randomUUID();
            UUID creatorId = UUID.randomUUID();

            // Act
            Project project = ProjectMother.standard()
                    .withOrganizationId(orgId)
                    .withName("My Awesome Project")
                    .withDescription("A cool description")
                    .withCreatedBy(creatorId)
                    .build();

            // Assert
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.getId()).isNotNull();
            assertThat(project.getOrganizationId()).isEqualTo(orgId);
            assertThat(project.getName()).isEqualTo("My Awesome Project");
            assertThat(project.getDescription()).isEqualTo("A cool description");
            assertThat(project.getTechnicalProfile()).isNotNull();

            assertThat(AggregateEvents.of(project))
                    .hasSize(1)
                    .allSatisfy(e -> {
                        assertThat(e).isInstanceOf(ProjectCreatedEvent.class);
                        ProjectCreatedEvent event = (ProjectCreatedEvent) e;
                        assertThat(event.projectId()).isEqualTo(project.getId());
                        assertThat(event.organizationId()).isEqualTo(orgId);
                        assertThat(event.createdBy()).isEqualTo(creatorId);
                    });
        }

        @Test
        @DisplayName("should reject blank name")
        void should_reject_blank_name() {
            assertThatThrownBy(() -> ProjectBuilder.aProject().withName("   ").build())
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("should reject null organizationId")
        void should_reject_null_organization_id() {
            assertThatThrownBy(() -> ProjectBuilder.aProject().withOrganizationId(null).build())
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("Updates")
    class Updates {

        @Test
        @DisplayName("should update details successfully")
        void should_update_details_successfully() {
            // Arrange
            Project project = ProjectMother.standard().build();
            com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile newProfile = 
                    new com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile(
                            java.util.List.of("Kotlin"),
                            java.util.List.of("Micronaut"),
                            java.util.List.of("Mobile"),
                            java.util.List.of("MongoDB"),
                            "Microservices",
                            "Logistics"
                    );

            // Act
            project.updateDetails("New Project Name", "New Description", newProfile);

            // Assert
            assertThat(project.getName()).isEqualTo("New Project Name");
            assertThat(project.getDescription()).isEqualTo("New Description");
            assertThat(project.getTechnicalProfile()).isEqualTo(newProfile);
        }

        @Test
        @DisplayName("should add project constraint successfully")
        void should_add_project_constraint_successfully() {
            Project project = ProjectMother.standard().build();

            ProjectConstraint constraint = project.addConstraint("Must integrate with SAP");

            assertThat(project.getConstraints()).hasSize(1);
            assertThat(constraint.getDescription()).isEqualTo("Must integrate with SAP");
        }

        @Test
        @DisplayName("should reject blank constraint description")
        void should_reject_blank_constraint_description() {
            Project project = ProjectMother.standard().build();

            assertThatThrownBy(() -> project.addConstraint("   "))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("should reject duplicate constraint ignoring case and trim")
        void should_reject_duplicate_constraint_ignoring_case_and_trim() {
            Project project = ProjectMother.standard().build();
            project.addConstraint("Must integrate with SAP");

            assertThatThrownBy(() -> project.addConstraint("  must integrate with sap  "))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should archive and reactivate project status")
        void should_archive_and_reactivate() {
            // Arrange
            Project project = ProjectMother.standard().build();
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);

            // Act & Assert
            project.archive();
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);

            project.activate();
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        }
    }
}
