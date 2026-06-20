package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectDocumentQuery;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Project Document")
@ExtendWith(MockitoExtension.class)
class GetProjectDocumentQueryHandlerTest {

    @Mock private OrganizationRepository organizations;
    @Mock private ProjectRepository projects;
    @Mock private ProjectDocumentRepository documents;
    @InjectMocks private GetProjectDocumentQueryHandler handler;

    @Test
    @DisplayName("should get project document successfully")
    void should_get_project_document_successfully() {
        Organization organization = OrganizationMother.active().build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        ProjectDocument document = new ProjectDocument(projectId, "Business Rules v1", DocumentType.BUSINESS_RULES);
        GetProjectDocumentQuery query = new GetProjectDocumentQuery(orgId, projectId, document.getId(), UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findByIdAndProjectIdAndStatus(document.getId(), projectId, DocumentStatus.ACTIVE))
                .thenReturn(Optional.of(document));

        ProjectDocument result = handler.handle(query);

        assertThat(result.getId()).isEqualTo(document.getId());
    }

    @Test
    @DisplayName("should fail if project document does not exist")
    void should_fail_if_project_document_does_not_exist() {
        Organization organization = OrganizationMother.active().build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        GetProjectDocumentQuery query = new GetProjectDocumentQuery(orgId, projectId, documentId, UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findByIdAndProjectIdAndStatus(documentId, projectId, DocumentStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(query))
                .isInstanceOf(DomainException.class);
    }
}
