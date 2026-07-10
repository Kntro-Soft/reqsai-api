package com.kntro.reqsai.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.billing.api.BillingModuleApi;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("Integration: Billing lifecycle (fake gateway)")
class BillingLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000021";
    private static final String ORG_ID = "00000000-0000-0000-0000-000000000029";

    @Autowired
    private BillingModuleApi billing;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("upgrade → usage → token metering → cancel → reactivate over the fake gateway")
    void full_commercial_cycle() throws Exception {
        String bearer = TestJwtFactory.bearer(USER_ID, ORG_ID, "ROLE_USER");
        String name = "Billing Cycle " + UUID.randomUUID().toString().substring(0, 8);

        UUID orgId = UUID.fromString((String) mapper.readValue(
                postOrg(Map.of("name", name, "meetingLanguage", "en-US"), bearer).getBody(), Map.class).get("id"));

        // Upgrade to PRO — fake gateway activates immediately.
        ResponseEntity<String> upgrade = put(orgId, "/upgrade", Map.of("planType", "PRO"), bearer);
        assertThat(upgrade.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> upgradeBody = mapper.readValue(upgrade.getBody(), Map.class);
        assertThat(upgradeBody.get("status")).isEqualTo("ACTIVATED");
        assertThat(upgradeBody.get("checkoutUrl")).isNull();
        assertThat(((Map<?, ?>) upgradeBody.get("subscription")).get("planType")).isEqualTo("PRO");

        // Usage reflects the PRO allowance with zero consumption.
        Map<?, ?> usage = mapper.readValue(get(orgId, "/usage", bearer).getBody(), Map.class);
        assertThat(usage.get("planType")).isEqualTo("PRO");
        assertThat(((Number) usage.get("tokensUsed")).longValue()).isZero();
        long limit = ((Number) usage.get("tokensLimit")).longValue();
        assertThat(limit).isGreaterThan(100_000L);

        // Meter tokens through the module API, then confirm usage moved.
        billing.recordTokenConsumption(orgId, 1_234L);
        Map<?, ?> usageAfter = mapper.readValue(get(orgId, "/usage", bearer).getBody(), Map.class);
        assertThat(((Number) usageAfter.get("tokensUsed")).longValue()).isEqualTo(1_234L);
        assertThat(((Number) usageAfter.get("tokensRemaining")).longValue()).isEqualTo(limit - 1_234L);

        // Cancel — stays usable but flips to CANCELLED.
        ResponseEntity<String> cancel = put(orgId, "/cancel", null, bearer);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readValue(cancel.getBody(), Map.class).get("status")).isEqualTo("CANCELLED");

        // Reactivate — back to ACTIVE.
        ResponseEntity<String> reactivate = put(orgId, "/reactivate", null, bearer);
        assertThat(reactivate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readValue(reactivate.getBody(), Map.class).get("status")).isEqualTo("ACTIVE");
    }

    private ResponseEntity<String> postOrg(Map<String, String> body, String bearer) {
        return client().post().uri("/api/organizations")
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> put(UUID orgId, String suffix, Map<String, String> body, String bearer) {
        var spec = client().put().uri("/api/subscriptions/organization/" + orgId + suffix)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }

    private ResponseEntity<String> get(UUID orgId, String suffix, String bearer) {
        return client().get().uri("/api/subscriptions/organization/" + orgId + suffix)
                .header("Authorization", bearer)
                .header("Api-Version", "1")
                .exchange((req, res) -> ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(String.class)));
    }
}
