package com.kntro.reqsai.shared.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link LanguageCode} value object.
 *
 * @see LanguageCode
 */
@DisplayName("Domain: LanguageCode Value Object")
class LanguageCodeTest {

    @Test
    @DisplayName("should normalize language and region casing")
    void should_normalize_casing() {
        // Act & Assert
        assertThat(LanguageCode.of("ES-pe").value()).isEqualTo("es-PE");
        assertThat(LanguageCode.of("EN").value()).isEqualTo("en");
        assertThat(LanguageCode.of("es_PE").value()).isEqualTo("es-PE");
    }

    @Test
    @DisplayName("should accept a UN M.49 numeric region (es-419)")
    void should_accept_numeric_region() {
        // Act & Assert
        assertThat(LanguageCode.of("es-419").value()).isEqualTo("es-419");
        assertThat(LanguageCode.of("ES-419").value()).isEqualTo("es-419");
    }

    @Test
    @DisplayName("should reject an invalid BCP-47 tag")
    void should_reject_invalid_tag() {
        // Act & Assert
        assertThatThrownBy(() -> LanguageCode.of("english")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> LanguageCode.of("")).isInstanceOf(DomainException.class);
    }
}
