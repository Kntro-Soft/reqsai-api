package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application: Demo project template")
class DemoProjectTemplateTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Test
    @DisplayName("builds an active demo project with the sample profile and constraints")
    void builds_a_valid_demo_project() {
        Project project = DemoProjectTemplate.newProject(ORG_ID, OWNER_ID);

        assertThat(project.isDemo()).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(project.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(project.getName()).isEqualTo(DemoProjectTemplate.NAME).startsWith("Demo");
        assertThat(project.getDescription()).isEqualTo(DemoProjectTemplate.DESCRIPTION);

        TechnicalProfile profile = project.getTechnicalProfile();
        assertThat(profile.domain()).isNotBlank();
        assertThat(profile.architecture()).isNotBlank();
        assertThat(profile.clientPlatforms()).contains("Web");
        assertThat(profile.frameworks()).isNotEmpty();

        assertThat(project.getConstraints())
                .extracting(ProjectConstraint::getDescription)
                .containsExactlyElementsOf(DemoProjectTemplate.constraints())
                .hasSizeBetween(2, 3);
    }

    @Test
    @DisplayName("fills a glossary with the sample terms attributed to the given user")
    void fills_the_glossary() {
        Glossary glossary = new Glossary(UUID.randomUUID());

        DemoProjectTemplate.resetGlossary(glossary, OWNER_ID);

        assertThat(glossary.getTerms())
                .extracting(GlossaryTerm::getTerm)
                .containsExactlyElementsOf(DemoProjectTemplate.glossary().stream()
                        .map(DemoProjectTemplate.GlossaryEntry::term).toList());
        assertThat(glossary.getTerms()).allSatisfy(term -> {
            assertThat(term.getDefinition()).isNotBlank();
            assertThat(term.getAddedBy()).isEqualTo(OWNER_ID);
        });
    }

    @Test
    @DisplayName("resetting the glossary replaces whatever the users added")
    void reset_glossary_replaces_user_terms() {
        Glossary glossary = new Glossary(UUID.randomUUID());
        glossary.addTerm("Mozo", "Persona que atiende las mesas.", OWNER_ID);
        glossary.addTerm("Comensal", "Definición editada por un usuario.", OWNER_ID);

        DemoProjectTemplate.resetGlossary(glossary, OWNER_ID);

        assertThat(glossary.getTerms()).hasSize(DemoProjectTemplate.glossary().size());
        assertThat(glossary.getTerms()).extracting(GlossaryTerm::getTerm).doesNotContain("Mozo");
        assertThat(glossary.getTerms())
                .filteredOn(t -> t.getTerm().equals("Comensal"))
                .singleElement()
                .extracting(GlossaryTerm::getDefinition)
                .isNotEqualTo("Definición editada por un usuario.");
    }

    @Test
    @DisplayName("resetting the project restores its profile and constraints but keeps the name")
    void reset_project_restores_profile_and_constraints() {
        Project project = DemoProjectTemplate.newProject(ORG_ID, OWNER_ID);
        project.updateDetails("Mi demo renombrada", "Otra descripción",
                new TechnicalProfile(List.of("Go"), List.of(), List.of(), List.of(), null, null));
        project.removeConstraint(project.getConstraints().getFirst().getId());
        project.updateConstraint(project.getConstraints().getFirst().getId(), "Texto editado por un usuario.");
        project.addConstraint("Una restricción agregada por un usuario.");

        DemoProjectTemplate.resetProject(project);

        assertThat(project.getName()).isEqualTo("Mi demo renombrada");
        assertThat(project.getDescription()).isEqualTo(DemoProjectTemplate.DESCRIPTION);
        assertThat(project.getTechnicalProfile()).isEqualTo(DemoProjectTemplate.profile());
        assertThat(project.getConstraints())
                .extracting(ProjectConstraint::getDescription)
                .containsExactlyInAnyOrderElementsOf(DemoProjectTemplate.constraints());
    }
}
