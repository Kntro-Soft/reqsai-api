package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Organization")
@ExtendWith(MockitoExtension.class)
class GetOrganizationQueryHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @InjectMocks
    private GetOrganizationQueryHandler handler;

    @Test
    @DisplayName("should return the organization when owned by the requester")
    void should_return_when_owned() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));

        assertThat(handler.handle(org.getId(), ownerId)).isSameAs(org);
    }

    @Test
    @DisplayName("should fail when the organization does not exist")
    void should_fail_when_missing() {
        UUID orgId = UUID.randomUUID();
        when(organizations.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(orgId, UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("should hide organizations owned by someone else as not found")
    void should_fail_when_not_owned() {
        Organization org = OrganizationMother.active().withOwnerId(UUID.randomUUID()).build();
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> handler.handle(org.getId(), UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
