package com.kntro.reqsai.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Assign Free Plan")
class AssignFreePlanIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ORG_ID = "00000000-0000-0000-0000-000000000009";

    @Autowired
    private SubscriptionRepositoryPort subscriptionRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("should automatically assign FREE subscription and configure correct limits when creating an organization")
    @SuppressWarnings("unchecked")
    void should_automatically_assign_free_subscription_on_org_creation() throws Exception {
        // Arrange
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String name = "Billing Acme " + suffix;
        String bearer = TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER");

        // Act - 1) Create the organization
        ResponseEntity<String> createResponse = postOrg(Map.of("name", name, "meetingLanguage", "en-US"), bearer);

        // Assert - 1) Verify organization creation response and limits
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> orgMap = mapper.readValue(createResponse.getBody(), Map.class);
        String orgIdStr = (String) orgMap.get("id");
        UUID organizationId = UUID.fromString(orgIdStr);

        // 2) Verify limits are applied on the organization aggregate in the database
        Optional<Organization> optOrg = organizationRepository.findById(organizationId);
        assertThat(optOrg).isPresent();
        PlanLimits planLimits = optOrg.get().getPlanLimits();
        assertThat(planLimits).isNotNull();
        assertThat(planLimits.maxMembers()).isEqualTo(3);
        assertThat(planLimits.maxProjects()).isEqualTo(25);
        assertThat(planLimits.maxDocumentsPerProject()).isEqualTo(10);
        assertThat(planLimits.maxTokensPerMonth()).isEqualTo(100_000L);
        assertThat(planLimits.maxGlossaryTermsPerProject()).isEqualTo(50);

        // 3) Verify subscription is persisted in public.subscriptions
        Optional<Subscription> optSub = subscriptionRepository.findByOrganizationId(organizationId);
        assertThat(optSub).isPresent();
        Subscription subscription = optSub.get();
        assertThat(subscription.isFree()).isTrue();
        assertThat(subscription.isActive()).isTrue();

        // Act/Assert - 4) Verify GET /api/subscriptions/organization/{orgId} endpoint
        ResponseEntity<String> getResponse = getSub(organizationId, bearer);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> subResponseMap = mapper.readValue(getResponse.getBody(), Map.class);
        assertThat(subResponseMap.get("organizationId")).isEqualTo(orgIdStr);
        assertThat(subResponseMap.get("planType")).isEqualTo("FREE");
        assertThat(subResponseMap.get("status")).isEqualTo("ACTIVE");
        assertThat(subResponseMap.get("provider")).isNull();
        assertThat(subResponseMap.get("providerExternalId")).isNull();
    }

    private ResponseEntity<String> postOrg(Map<String, String> body, String bearer) {
        return client().post().uri("/api/organizations")
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private ResponseEntity<String> getSub(UUID orgId, String bearer) {
        return client().get().uri("/api/subscriptions/organization/" + orgId)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }
}
