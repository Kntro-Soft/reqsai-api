package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the DiscoverySession aggregate root (creation slice).
 *
 * @see DiscoverySession
 */
@DisplayName("Domain: DiscoverySession Aggregate")
class DiscoverySessionTest {

    @Test
    @DisplayName("should create the session in DRAFT")
    void should_create_in_draft() {
        // Act
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DRAFT);
        assertThat(session.getId()).isNotNull();
        assertThat(session.getProjectId()).isNotNull();
        assertThat(session.getLanguage()).isNotNull();
    }

    @Test
    @DisplayName("should reject a blank title")
    void should_reject_blank_title() {
        // Act & Assert
        assertThatThrownBy(() -> DiscoverySessionMother.draft().withTitle("  ").build())
                .isInstanceOf(DomainException.class);
    }
}
