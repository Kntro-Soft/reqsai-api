package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.DemoProjectSeededIntegrationEvent;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: Demo project seeder")
@ExtendWith(MockitoExtension.class)
class DemoProjectSeederTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Mock
    private ProjectRepository projects;
    @Mock
    private GlossaryRepository glossaries;
    @Mock
    private ApplicationEventPublisher events;
    @InjectMocks
    private DemoProjectSeeder seeder;

    @Test
    @DisplayName("provision creates the demo, fills its glossary and announces it")
    void provision_creates_and_announces() {
        when(projects.existsDemoByOrganizationId(ORG_ID)).thenReturn(false);
        Glossary glossary = new Glossary(UUID.randomUUID());
        when(glossaries.findByProjectId(any())).thenReturn(Optional.of(glossary));

        Optional<Project> created = seeder.provision(ORG_ID, OWNER_ID);

        assertThat(created).isPresent();
        Project project = created.get();
        assertThat(project.isDemo()).isTrue();
        assertThat(project.getAvatar()).isNull();
        verify(projects).save(project);
        assertThat(glossary.getTerms()).hasSize(DemoProjectTemplate.glossary().size());
        verify(glossaries).save(glossary);
        ArgumentCaptor<DemoProjectSeededIntegrationEvent> event =
                ArgumentCaptor.forClass(DemoProjectSeededIntegrationEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().organizationId()).isEqualTo(ORG_ID);
        assertThat(event.getValue().projectId()).isEqualTo(project.getId());
        assertThat(event.getValue().requestedBy()).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("provision is idempotent: an organization that has its demo is left untouched")
    void provision_is_idempotent() {
        when(projects.existsDemoByOrganizationId(ORG_ID)).thenReturn(true);

        assertThat(seeder.provision(ORG_ID, OWNER_ID)).isEmpty();

        verify(projects, never()).save(any());
        verifyNoInteractions(glossaries, events);
    }

    @Test
    @DisplayName("restore resets the demo and announces it for the caller")
    void restore_resets_and_announces() {
        Project demo = DemoProjectTemplate.newProject(ORG_ID, OWNER_ID);
        UUID caller = UUID.randomUUID();
        when(glossaries.findByProjectId(demo.getId())).thenReturn(Optional.empty());

        Project restored = seeder.restore(demo, caller);

        assertThat(restored).isSameAs(demo);
        verify(projects).save(demo);
        ArgumentCaptor<Glossary> glossary = ArgumentCaptor.forClass(Glossary.class);
        verify(glossaries).save(glossary.capture());
        assertThat(glossary.getValue().getProjectId()).isEqualTo(demo.getId());
        assertThat(glossary.getValue().getTerms()).hasSize(DemoProjectTemplate.glossary().size());
        ArgumentCaptor<DemoProjectSeededIntegrationEvent> event =
                ArgumentCaptor.forClass(DemoProjectSeededIntegrationEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().requestedBy()).isEqualTo(caller);
    }
}
