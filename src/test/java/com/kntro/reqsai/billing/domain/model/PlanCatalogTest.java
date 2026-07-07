package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanLimitsValues;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("should throw on unconfigured tiers")
    void should_throw_on_unconfigured_tiers() {
        assertThatThrownBy(() -> PlanCatalog.limitsFor(PlanType.PRO))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not yet defined");
    }
}
