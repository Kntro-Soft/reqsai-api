package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Slug} value object.
 *
 * @see Slug
 */
@DisplayName("Domain: Slug Value Object")
class SlugTest {

    @Test
    @DisplayName("should slugify a free-text name")
    void should_slugify_name() {
        // Act & Assert
        assertThat(Slug.fromName("Acme Corp").value()).isEqualTo("acme-corp");
        assertThat(Slug.fromName("  Tám Ciber Perú!! ").value()).isEqualTo("tam-ciber-peru");
        assertThat(Slug.fromName("A & B  --  C").value()).isEqualTo("a-b-c");
    }

    @Test
    @DisplayName("should accept a valid explicit slug")
    void should_accept_valid_explicit_slug() {
        // Act & Assert
        assertThat(Slug.of("valid-slug-1").value()).isEqualTo("valid-slug-1");
    }

    @Test
    @DisplayName("should reject an invalid slug")
    void should_reject_invalid_slug() {
        // Act & Assert
        assertThatThrownBy(() -> Slug.of("Invalid Slug")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Slug.of("-bad")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Slug.of("")).isInstanceOf(DomainException.class);
    }
}
