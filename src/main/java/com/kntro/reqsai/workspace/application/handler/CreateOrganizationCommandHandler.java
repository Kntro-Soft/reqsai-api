package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.billing.api.PlanLimitsSnapshot;
import com.kntro.reqsai.shared.application.avatar.AvatarDownloadPort;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.ProvisioningService;
import com.kntro.reqsai.workspace.application.command.CreateOrganizationCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.GenerationSettings;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.domain.valueobjects.Slug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates an organization and provisions its tenant schema.
 * <p>
 * Flow: validate slug uniqueness → persist the org {@code PENDING} → provision the {@code tenant_<slug>}
 * schema (DDL + Flyway) → {@link Organization#activate()} (flip to {@code ACTIVE}, raise
 * {@code OrganizationCreatedEvent}) → persist. Deliberately <strong>not</strong> {@code @Transactional}:
 * schema creation/Flyway run their own connections and must not sit inside a JPA transaction, and a
 * {@code PENDING} row left by a provisioning failure is harmless (the resolver ignores {@code PENDING}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrganizationCommandHandler {

    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final String AVATAR_URL_TEMPLATE = "https://avatar.vercel.sh/%s.svg";

    private final OrganizationRepository organizations;
    private final ProvisioningService provisioningService;
    private final AvatarDownloadPort avatarDownloadAdapter;
    private final BillingModuleApi billing;

    public Organization handle(CreateOrganizationCommand command) {
        Slug slug = (command.slug() != null && !command.slug().isBlank())
                ? Slug.of(command.slug())
                : Slug.fromName(command.name());

        if (organizations.existsBySlug(slug)) {
            throw WorkspaceExceptions.slugAlreadyExists(slug.value());
        }

        GenerationSettings settings = (command.meetingLanguage() != null && !command.meetingLanguage().isBlank())
                ? GenerationSettings.of(LanguageCode.of(command.meetingLanguage()), DEFAULT_RETENTION_DAYS)
                : GenerationSettings.defaults();

        PlanLimitsSnapshot free = billing.freePlanLimits();
        PlanLimits planLimits = new PlanLimits(
                free.maxMembers(),
                free.maxProjects(),
                free.maxDocumentsPerProject(),
                free.maxTokensPerMonth(),
                free.maxGlossaryTermsPerProject()
        );

        Organization organization = new Organization(command.name(), slug, command.requestedBy(), settings, planLimits);
        organizations.save(organization);
        log.info("Organization {} persisted as PENDING (slug={})", organization.getId(), slug.value());

        provisioningService.provisionTenant(slug.value());

        avatarDownloadAdapter.download(AVATAR_URL_TEMPLATE.formatted(slug.value()))
                .ifPresent(avatar -> organization.applyAvatar(avatar.bytes(), avatar.contentType()));

        organization.activate();
        organizations.save(organization);
        log.info("Organization {} activated", organization.getId());

        billing.assignFreeSubscription(organization.getId());

        return organization;
    }
}
