package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain: what a discovery session is for")
class DiscoverySessionPurposeTest {

    @Test
    @DisplayName("a session is a discovery meeting unless created as a clarification")
    void defaultsToDiscovery() {
        DiscoverySession session = new DiscoverySession(UUID.randomUUID(), "Kickoff", LanguageCode.of("es-PE"));

        assertThat(session.getPurpose()).isEqualTo(SessionPurpose.DISCOVERY);
    }

    @Test
    @DisplayName("a clarification meeting keeps its purpose and starts in DRAFT like any session")
    void clarification() {
        DiscoverySession session = new DiscoverySession(UUID.randomUUID(), "Aclaración", LanguageCode.of("es-PE"),
                SessionPurpose.CLARIFICATION);

        assertThat(session.getPurpose()).isEqualTo(SessionPurpose.CLARIFICATION);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DRAFT);
    }

    @Test
    @DisplayName("the purpose is required")
    void required() {
        assertThatThrownBy(() -> new DiscoverySession(UUID.randomUUID(), "x", LanguageCode.of("es-PE"), null))
                .isInstanceOf(com.kntro.reqsai.shared.domain.exception.DomainException.class);
    }
}
