package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.api.DemoProjectSeededIntegrationEvent;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Writes the demo project's sample content ({@link DemoProjectTemplate}) in the <em>currently bound
 * tenant</em>, then announces it with {@link DemoProjectSeededIntegrationEvent} so Discovery (re)seeds its
 * own part in the same transaction.
 * <ul>
 *   <li>{@link #provision} creates the demo project of a freshly provisioned organization. Idempotent: an
 *       organization that already has its demo project is left untouched (also guarded by the
 *       {@code uq_projects_org_demo} partial unique index).</li>
 *   <li>{@link #restore} puts an existing demo project back to its original content.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoProjectSeeder {

    private final ProjectRepository projects;
    private final GlossaryRepository glossaries;
    private final ApplicationEventPublisher events;

    /**
     * Creates the organization's demo project with its sample content. Runs in its own transaction (it is
     * triggered after the organization's commit); the caller binds the new tenant before calling. Unlike a
     * user-created project, no avatar is downloaded: seeding makes no external call, so it never slows
     * down onboarding, and the UI falls back to the project's initials.
     *
     * @return the created project, or empty when the organization already had one
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Project> provision(UUID organizationId, UUID ownerId) {
        if (projects.existsDemoByOrganizationId(organizationId)) {
            log.info("Organization {} already has its demo project; nothing to seed", organizationId);
            return Optional.empty();
        }

        Project project = DemoProjectTemplate.newProject(organizationId, ownerId);
        projects.save(project);

        seedGlossary(project.getId(), ownerId);
        events.publishEvent(DemoProjectSeededIntegrationEvent.of(organizationId, project.getId(), ownerId));
        log.info("Demo project {} seeded for organization {}", project.getId(), organizationId);
        return Optional.of(project);
    }

    /**
     * Restores the demo project's original content — profile, constraints and glossary here, the discovery
     * data through the published event. Joins the caller's transaction, so the restore is all or nothing.
     */
    @Transactional
    public Project restore(Project project, UUID requestedBy) {
        project.requireDemo();
        DemoProjectTemplate.resetProject(project);
        projects.save(project);

        seedGlossary(project.getId(), requestedBy);
        events.publishEvent(DemoProjectSeededIntegrationEvent.of(
                project.getOrganizationId(), project.getId(), requestedBy));
        log.info("Demo project {} restored to its sample content", project.getId());
        return project;
    }

    private void seedGlossary(UUID projectId, UUID addedBy) {
        Glossary glossary = glossaries.findByProjectId(projectId)
                .orElseGet(() -> new Glossary(projectId));
        DemoProjectTemplate.resetGlossary(glossary, addedBy);
        glossaries.save(glossary);
    }
}
