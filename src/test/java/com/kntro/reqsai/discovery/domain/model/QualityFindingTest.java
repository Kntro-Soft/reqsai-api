package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("QualityFinding")
class QualityFindingTest {

    @Test
    @DisplayName("a met characteristic scores 100 and has nothing to fix")
    void met() {
        QualityFinding finding = QualityFinding.met(QualityCharacteristic.AMBIGUITY, "  Sin términos ambiguos. ");

        assertThat(finding.score()).isEqualTo(100);
        assertThat(finding.met()).isTrue();
        assertThat(finding.observation()).isEqualTo("Sin términos ambiguos.");
        assertThat(finding.suggestion()).isNull();
    }

    @Test
    @DisplayName("an unmet characteristic says what was found and how to fix it")
    void unmet() {
        QualityFinding finding = QualityFinding.unmet(QualityCharacteristic.VERIFIABILITY, 0, "Sin criterios.",
                " Agrega criterios. ");

        assertThat(finding.met()).isFalse();
        assertThat(finding.score()).isZero();
        assertThat(finding.suggestion()).isEqualTo("Agrega criterios.");
    }

    @Test
    @DisplayName("an unmet characteristic cannot score 100 and a score below 100 needs a suggestion")
    void unmet_rules() {
        assertThatThrownBy(() -> QualityFinding.unmet(QualityCharacteristic.ATOMICITY, 100, "obs", "fix"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new QualityFinding(QualityCharacteristic.ATOMICITY, 60, "obs", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new QualityFinding(QualityCharacteristic.ATOMICITY, 60, "obs", "  "))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("refuses scores outside 0..100, a missing characteristic and a blank observation")
    void invariants() {
        assertThatThrownBy(() -> new QualityFinding(QualityCharacteristic.ATOMICITY, -1, "obs", "fix"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new QualityFinding(QualityCharacteristic.ATOMICITY, 101, "obs", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new QualityFinding(null, 100, "obs", null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new QualityFinding(QualityCharacteristic.ATOMICITY, 100, " ", null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("refuses text over its limit")
    void text_limit() {
        assertThatThrownBy(() -> QualityFinding.met(QualityCharacteristic.ATOMICITY, "x".repeat(1001)))
                .isInstanceOf(DomainException.class);
    }
}
