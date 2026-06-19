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
}
