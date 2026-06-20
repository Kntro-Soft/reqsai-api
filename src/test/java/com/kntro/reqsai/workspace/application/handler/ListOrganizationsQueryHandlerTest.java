package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Organizations")
@ExtendWith(MockitoExtension.class)
class ListOrganizationsQueryHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @InjectMocks
    private ListOrganizationsQueryHandler handler;

    @Test
    @DisplayName("should return the organizations owned by the user")
    void should_return_owned_organizations() {
        UUID ownerId = UUID.randomUUID();
        List<Organization> owned = List.of(
                OrganizationMother.active().withOwnerId(ownerId).build(),
                OrganizationMother.active().withOwnerId(ownerId).build());
        when(organizations.findAllByOwnerId(ownerId)).thenReturn(owned);

        assertThat(handler.handle(ownerId)).hasSize(2).isEqualTo(owned);
    }

    @Test
    @DisplayName("should return an empty list when the user owns none")
    void should_return_empty_when_none() {
        UUID ownerId = UUID.randomUUID();
        when(organizations.findAllByOwnerId(ownerId)).thenReturn(List.of());

        assertThat(handler.handle(ownerId)).isEmpty();
    }
}
