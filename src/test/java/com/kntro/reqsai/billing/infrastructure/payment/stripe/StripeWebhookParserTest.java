package com.kntro.reqsai.billing.infrastructure.payment.stripe;

import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.PaymentWebhookEvent;
import com.kntro.reqsai.billing.domain.exception.BillingError;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Infrastructure: StripeWebhookParser")
class StripeWebhookParserTest {

    private static final String SECRET = "whsec_test_secret";

    private final StripeWebhookParser parser = new StripeWebhookParser(
            new BillingProperties("stripe", "USD", null,
                    new BillingProperties.Stripe("sk_test", SECRET, null, null)),
            new ObjectMapper());

    private static String sign(String payload) {
        long ts = 1_700_000_000L;
        String signed = ts + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String v1 = HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
            return "t=" + ts + ",v1=" + v1;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("verifies signature and maps checkout.session.completed to PLAN_ACTIVATED")
    void should_parse_checkout_completed() {
        UUID orgId = UUID.randomUUID();
        String payload = """
                {"id":"evt_1","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_1","subscription":"sub_123","client_reference_id":"%s",
                  "metadata":{"organizationId":"%s","targetPlan":"PRO"}}}}
                """.formatted(orgId, orgId);

        PaymentWebhookEvent event = parser.verifyAndParse(payload, sign(payload));

        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.kind()).isEqualTo(PaymentWebhookEvent.Kind.PLAN_ACTIVATED);
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(event.targetPlan()).isEqualTo(PlanType.PRO);
        assertThat(event.externalSubscriptionId()).isEqualTo("sub_123");
    }

    @Test
    @DisplayName("maps customer.subscription.deleted to SUBSCRIPTION_DELETED")
    void should_parse_subscription_deleted() {
        String payload = """
                {"id":"evt_2","type":"customer.subscription.deleted","data":{"object":{"id":"sub_999"}}}
                """;

        PaymentWebhookEvent event = parser.verifyAndParse(payload, sign(payload));

        assertThat(event.kind()).isEqualTo(PaymentWebhookEvent.Kind.SUBSCRIPTION_DELETED);
        assertThat(event.externalSubscriptionId()).isEqualTo("sub_999");
    }

    @Test
    @DisplayName("unknown event types map to IGNORED")
    void should_ignore_unknown_types() {
        String payload = """
                {"id":"evt_3","type":"customer.created","data":{"object":{"id":"cus_1"}}}
                """;

        PaymentWebhookEvent event = parser.verifyAndParse(payload, sign(payload));

        assertThat(event.kind()).isEqualTo(PaymentWebhookEvent.Kind.IGNORED);
    }

    @Test
    @DisplayName("rejects a tampered payload / bad signature")
    void should_reject_bad_signature() {
        String payload = "{\"id\":\"evt_4\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{}}}";
        String badSig = sign(payload).replace("v1=", "v1=deadbeef");

        assertThatThrownBy(() -> parser.verifyAndParse(payload, badSig))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(BillingError.WEBHOOK_SIGNATURE_INVALID);
    }
}
