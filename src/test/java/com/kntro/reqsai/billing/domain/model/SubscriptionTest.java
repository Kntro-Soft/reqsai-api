package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.billing.domain.model.valueobjects.SubscriptionStatus;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
