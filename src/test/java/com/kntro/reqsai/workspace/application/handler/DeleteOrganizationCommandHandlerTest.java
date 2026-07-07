package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.ProvisioningService;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.command.DeleteOrganizationCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.OrgStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Delete Organization")
@ExtendWith(MockitoExtension.class)
class DeleteOrganizationCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProvisioningService provisioningService;
    @Mock
    private TenantSchemaResolver tenantSchemaResolver;
    @InjectMocks
    private DeleteOrganizationCommandHandler handler;

    @Test
    @DisplayName("owner deletes org: soft-deletes and deprovisions tenant")
    void owner_deletes_org() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).withSlug("acme").build();

        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(organizations.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

        handler.handle(new DeleteOrganizationCommand(org.getId(), ownerId));

        assertThat(org.getStatus()).isEqualTo(OrgStatus.DELETED);
        verify(organizations).save(org);
        verify(tenantSchemaResolver).evictTenantSchema(org.getId().toString());
        verify(provisioningService).deprovisionTenant("acme");
    }
}
