package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain: PlanCatalog")
class PlanCatalogTest {

    @Test
    @DisplayName("should resolve limits for FREE tier")
    void should_resolve_free_limits() {
        PlanLimitsValues limits = PlanCatalog.limitsFor(PlanType.FREE);
        assertThat(limits.maxMembers()).isEqualTo(3);
        assertThat(limits.maxProjects()).isEqualTo(25);
        assertThat(limits.maxDocumentsPerProject()).isEqualTo(10);
        assertThat(limits.maxTokensPerMonth()).isEqualTo(100_000L);
        assertThat(limits.maxGlossaryTermsPerProject()).isEqualTo(50);
    }

    @Test
    @DisplayName("should resolve richer limits for paid tiers")
    void should_resolve_paid_tier_limits() {
        PlanLimitsValues pro = PlanCatalog.limitsFor(PlanType.PRO);
        PlanLimitsValues enterprise = PlanCatalog.limitsFor(PlanType.ENTERPRISE);

        assertThat(pro.maxTokensPerMonth()).isGreaterThan(PlanCatalog.limitsFor(PlanType.FREE).maxTokensPerMonth());
        assertThat(enterprise.maxTokensPerMonth()).isGreaterThan(pro.maxTokensPerMonth());
        assertThat(enterprise.maxProjects()).isGreaterThan(pro.maxProjects());
    }

    @Test
    @DisplayName("only paid tiers are purchasable")
    void should_flag_paid_tiers_as_purchasable() {
        assertThat(PlanCatalog.isPurchasable(PlanType.FREE)).isFalse();
        assertThat(PlanCatalog.isPurchasable(PlanType.PRO)).isTrue();
        assertThat(PlanCatalog.isPurchasable(PlanType.ENTERPRISE)).isTrue();
    }
}
