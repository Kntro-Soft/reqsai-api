package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProvider;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.billing.domain.model.valueobjects.SubscriptionStatus;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: Subscription Aggregate")
class SubscriptionTest {

    @Test
    @DisplayName("new Subscription(orgId) should create FREE active subscription")
    void should_create_free_active_subscription() {
        UUID orgId = UUID.randomUUID();
        Subscription sub = new Subscription(orgId);

        assertThat(sub.getId()).isNotNull();
        assertThat(sub.getOrganizationId()).isEqualTo(orgId);
        assertThat(sub.getPlanType()).isEqualTo(PlanType.FREE);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getTokenQuotaUsed()).isEqualTo(0L);
        assertThat(sub.isFree()).isTrue();
        assertThat(sub.isActive()).isTrue();
        assertThat(AggregateEvents.of(sub)).hasSize(1);
    }

    private static PaymentProviderRef ref() {
        return PaymentProviderRef.of(PaymentProvider.STRIPE, "sub_" + UUID.randomUUID());
    }

    @Test
    @DisplayName("upgradeTo should switch to the paid plan, reset quota and store the provider ref")
    void should_upgrade_to_paid_plan() {
        Subscription sub = new Subscription(UUID.randomUUID());
        sub.recordTokenUsage(500);

        PaymentProviderRef ref = ref();
        sub.upgradeTo(PlanType.PRO, ref);

        assertThat(sub.getPlanType()).isEqualTo(PlanType.PRO);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getProviderRef()).isEqualTo(ref);
        assertThat(sub.getTokenQuotaUsed()).isZero();
        assertThat(sub.isFree()).isFalse();
    }

    @Test
    @DisplayName("upgradeTo FREE should be rejected")
    void should_reject_upgrade_to_free() {
        Subscription sub = new Subscription(UUID.randomUUID());
        assertThatThrownBy(() -> sub.upgradeTo(PlanType.FREE, ref()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("cancel should mark CANCELLED with a timestamp; FREE cannot be cancelled")
    void should_cancel_paid_subscription() {
        Subscription sub = new Subscription(UUID.randomUUID());
        assertThatThrownBy(sub::cancel).isInstanceOf(DomainException.class);

        sub.upgradeTo(PlanType.PRO, ref());
        sub.cancel();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(sub.getCancelledAt()).isNotNull();
        assertThat(sub.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("reactivate should resume a cancelled subscription; active cannot be reactivated")
    void should_reactivate_cancelled_subscription() {
        Subscription sub = new Subscription(UUID.randomUUID());
        sub.upgradeTo(PlanType.PRO, ref());
        assertThatThrownBy(sub::reactivate).isInstanceOf(DomainException.class);

        sub.cancel();
        sub.reactivate();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("downgradeToFree should revert plan and clear the provider ref; idempotent on FREE")
    void should_downgrade_to_free() {
        Subscription sub = new Subscription(UUID.randomUUID());
        sub.upgradeTo(PlanType.ENTERPRISE, ref());

        sub.downgradeToFree();

        assertThat(sub.getPlanType()).isEqualTo(PlanType.FREE);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getProviderRef()).isNull();

        // idempotent: a second downgrade registers no additional downgrade event
        int eventsBefore = AggregateEvents.of(sub).size();
        sub.downgradeToFree();
        assertThat(AggregateEvents.of(sub)).hasSize(eventsBefore);
    }

    @Test
    @DisplayName("recordTokenUsage should accumulate within the current period")
    void should_accumulate_token_usage() {
        Subscription sub = new Subscription(UUID.randomUUID());
        sub.recordTokenUsage(1_200);
        sub.recordTokenUsage(300);
        sub.recordTokenUsage(0);   // ignored
        sub.recordTokenUsage(-50); // ignored

        assertThat(sub.getTokenQuotaUsed()).isEqualTo(1_500L);
    }

    @Test
    @DisplayName("markPastDue should flag a paid subscription; no-op on FREE")
    void should_mark_past_due() {
        Subscription free = new Subscription(UUID.randomUUID());
        free.markPastDue();
        assertThat(free.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        Subscription paid = new Subscription(UUID.randomUUID());
        paid.upgradeTo(PlanType.PRO, ref());
        paid.markPastDue();
        assertThat(paid.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }
}
