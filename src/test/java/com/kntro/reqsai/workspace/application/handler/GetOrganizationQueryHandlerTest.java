package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.GetOrganizationQuery;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("Successful retrieval")
    class SuccessfulRetrieval {

        @Test
        @DisplayName("should return organization when requested by owner")
        void should_return_organization_when_requested_by_owner() {
            UUID ownerId = UUID.randomUUID();
            Organization organization = OrganizationMother.active().withOwnerId(ownerId).build();

            when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));

            Organization result = handler.handle(new GetOrganizationQuery(organization.getId(), ownerId));

            assertThat(result).isEqualTo(organization);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();

            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(new GetOrganizationQuery(orgId, requestedBy)))
                    .isInstanceOf(DomainException.class);
        }
    }
}
