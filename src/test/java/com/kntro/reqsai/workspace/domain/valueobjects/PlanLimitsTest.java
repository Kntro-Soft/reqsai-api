package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PlanLimits} value object.
 *
 * @see PlanLimits
 */
@DisplayName("Domain: PlanLimits Value Object")
class PlanLimitsTest {

    @Test
    @DisplayName("should construct valid PlanLimits")
    void should_construct_valid_plan_limits() {
        // Act
        PlanLimits limits = new PlanLimits(3, 25, 10, 100_000L, 50);

        // Assert
        assertThat(limits.maxMembers()).isEqualTo(3);
        assertThat(limits.maxProjects()).isEqualTo(25);
        assertThat(limits.maxDocumentsPerProject()).isEqualTo(10);
        assertThat(limits.maxTokensPerMonth()).isEqualTo(100_000L);
        assertThat(limits.maxGlossaryTermsPerProject()).isEqualTo(50);
    }

    @Test
    @DisplayName("should allow -1 (unlimited)")
    void should_allow_unlimited() {
        // Act
        PlanLimits unlimited = new PlanLimits(-1, -1, -1, -1L, -1);

        // Assert
        assertThat(unlimited.maxProjects()).isEqualTo(-1);
        assertThat(unlimited.maxTokensPerMonth()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("should reject a value below -1")
    void should_reject_value_below_minus_one() {
        // Act & Assert
        assertThatThrownBy(() -> new PlanLimits(-2, 1, 10, 1L, 50)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PlanLimits(3, 1, 10, -2L, 50)).isInstanceOf(DomainException.class);
    }
}
