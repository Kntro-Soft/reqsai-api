package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link GenerationSettings} value object.
 *
 * @see GenerationSettings
 */
@DisplayName("Domain: GenerationSettings Value Object")
class GenerationSettingsTest {

    @Test
    @DisplayName("should provide sensible defaults")
    void should_provide_defaults() {
        // Assert
        assertThat(GenerationSettings.defaults().audioRetentionDays()).isGreaterThanOrEqualTo(0);
        assertThat(GenerationSettings.defaults().meetingLanguage()).isNotNull();
    }

    @Test
    @DisplayName("should allow -1 (keep forever) as retention")
    void should_allow_keep_forever_retention() {
        // Assert
        assertThat(GenerationSettings.of(LanguageCode.of("es-PE"), -1).audioRetentionDays()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should reject a retention below -1")
    void should_reject_retention_below_minus_one() {
        // Act & Assert
        assertThatThrownBy(() -> GenerationSettings.of(LanguageCode.of("es-PE"), -2))
                .isInstanceOf(DomainException.class);
    }
}
