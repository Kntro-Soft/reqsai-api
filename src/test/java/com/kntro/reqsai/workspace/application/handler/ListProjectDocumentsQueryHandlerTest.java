package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectDocumentsQuery;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Project Documents")
@ExtendWith(MockitoExtension.class)
class ListProjectDocumentsQueryHandlerTest {

    @Mock private OrganizationRepository organizations;
    @Mock private ProjectRepository projects;
    @Mock private ProjectDocumentRepository documents;
    @InjectMocks private ListProjectDocumentsQueryHandler handler;

    @Test
    @DisplayName("should list project documents successfully")
    void should_list_project_documents_successfully() {
        Organization organization = OrganizationMother.active().build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        ListProjectDocumentsQuery query = new ListProjectDocumentsQuery(orgId, projectId, UUID.randomUUID());
        List<ProjectDocument> expected = List.of(
                new ProjectDocument(projectId, "Business Rules v1", DocumentType.BUSINESS_RULES),
                new ProjectDocument(projectId, "Technical Spec v1", DocumentType.TECHNICAL_SPEC));

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findAllByProjectIdAndStatus(projectId, DocumentStatus.ACTIVE)).thenReturn(expected);

        List<ProjectDocument> result = handler.handle(query);

        assertThat(result).hasSize(2);
    }
}
