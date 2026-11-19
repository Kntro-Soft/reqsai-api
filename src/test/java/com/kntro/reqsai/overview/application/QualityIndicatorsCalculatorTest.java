package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.DecidedStory;
import com.kntro.reqsai.discovery.insights.HeldSession;
import com.kntro.reqsai.discovery.insights.QualityEvidence;
import com.kntro.reqsai.overview.application.result.AcceptanceIndicator;
import com.kntro.reqsai.overview.application.result.PostProcessingIndicator;
import com.kntro.reqsai.overview.application.result.QualityIndicators;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Application: quality indicator formulas")
class QualityIndicatorsCalculatorTest {

    private static final LocalDate FROM = LocalDate.parse("2026-10-20");
    private static final LocalDate TO = LocalDate.parse("2026-11-18");
    private static final Instant ENDED = Instant.parse("2026-11-10T15:00:00Z");
    private static final String TEXT = "reservar mesa\ncomensal\nreservar una mesa\nno hacer fila\n"
            + "dado un horario | cuando reserva | entonces queda confirmada";

    private static DecidedStory story(String status, String generated, String current) {
        return new DecidedStory(UUID.randomUUID(), status, generated, current);
    }

    private static HeldSession session(String purpose, Instant endedAt, Instant finishedAt) {
        return new HeldSession(UUID.randomUUID(), "Sesión", purpose, endedAt, finishedAt);
    }

    private static QualityEvidence evidence(List<DecidedStory> stories, long withoutBaseline,
            List<HeldSession> sessions) {
        return new QualityEvidence(stories, withoutBaseline, sessions);
    }

    @Test
    @DisplayName("a period without meetings or decisions has no data and no figure invented")
    void emptyPeriod() {
        QualityIndicators indicators = QualityIndicatorsCalculator.calculate(FROM, TO, evidence(List.of(), 0, List.of()));

        assertThat(indicators.hasData()).isFalse();
        assertThat(indicators.sessions()).isZero();
        assertThat(indicators.acceptance().decided()).isZero();
        assertThat(indicators.acceptance().rate()).isNull();
        assertThat(indicators.postProcessing().averageSeconds()).isNull();
        assertThat(indicators.postProcessing().medianSeconds()).isNull();
        assertThat(indicators.postProcessing().sessions()).isEmpty();
        assertThat(indicators.clarification().meetings()).isZero();
        assertThat(indicators.clarification().share()).isNull();
        assertThat(indicators.from()).isEqualTo(FROM);
        assertThat(indicators.to()).isEqualTo(TO);
    }

    @Test
    @DisplayName("stories without baseline alone still make the period one with data, with a null rate")
    void onlyStoriesWithoutBaseline() {
        QualityIndicators indicators = QualityIndicatorsCalculator.calculate(FROM, TO, evidence(List.of(), 2, List.of()));

        assertThat(indicators.hasData()).isTrue();
        assertThat(indicators.acceptance().withoutBaseline()).isEqualTo(2);
        assertThat(indicators.acceptance().rate()).isNull();
    }

    @Test
    @DisplayName("rate = accepted stories kept close to the generated text over every decided story")
    void acceptanceRate() {
        String rewritten = "otra historia completamente distinta\nadministrador\nconfigurar el sistema\npara operar mejor";
        QualityEvidence evidence = evidence(List.of(
                story("APPROVED", TEXT, TEXT),
                story("EXPORTED", TEXT, TEXT.toUpperCase()),
                story("APPROVED", TEXT, rewritten),
                story("REJECTED", TEXT, TEXT),
                story("REJECTED", TEXT, rewritten)), 1, List.of(session("DISCOVERY", ENDED, null)));

        AcceptanceIndicator acceptance = QualityIndicatorsCalculator.calculate(FROM, TO, evidence).acceptance();

        assertThat(acceptance.decided()).isEqualTo(5);
        assertThat(acceptance.accepted()).isEqualTo(3);
        assertThat(acceptance.acceptedAsGenerated()).isEqualTo(2);
        assertThat(acceptance.acceptedRewritten()).isEqualTo(1);
        assertThat(acceptance.rejected()).isEqualTo(2);
        assertThat(acceptance.withoutBaseline()).isEqualTo(1);
        assertThat(acceptance.similarityThreshold()).isEqualTo(QualityIndicatorsCalculator.SIMILARITY_THRESHOLD);
        assertThat(acceptance.rate()).isCloseTo(2.0 / 5.0, within(1e-9));
    }

    @Test
    @DisplayName("the 80% threshold is inclusive: 20 edits over 100 characters is kept, 21 is a rewrite")
    void thresholdBoundary() {
        String generated = "a".repeat(100);
        String atLimit = "b".repeat(20) + "a".repeat(80);
        String overLimit = "b".repeat(21) + "a".repeat(79);

        AcceptanceIndicator acceptance = QualityIndicatorsCalculator.acceptance(evidence(List.of(
                story("APPROVED", generated, atLimit), story("APPROVED", generated, overLimit)), 0, List.of()));

        assertThat(acceptance.acceptedAsGenerated()).isEqualTo(1);
        assertThat(acceptance.acceptedRewritten()).isEqualTo(1);
        assertThat(acceptance.rate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("a rejected story counts in the denominator whatever its text, never in the numerator")
    void rejectedNeverCounts() {
        AcceptanceIndicator acceptance = QualityIndicatorsCalculator
                .acceptance(evidence(List.of(story("REJECTED", TEXT, TEXT)), 0, List.of()));

        assertThat(acceptance.decided()).isEqualTo(1);
        assertThat(acceptance.acceptedAsGenerated()).isZero();
        assertThat(acceptance.rate()).isZero();
    }

    @Test
    @DisplayName("only rewritten stories give a rate of zero; only kept stories give one")
    void extremes() {
        assertThat(QualityIndicatorsCalculator.acceptance(evidence(List.of(story("APPROVED", TEXT, TEXT)), 0, List.of()))
                .rate()).isEqualTo(1.0);
        assertThat(QualityIndicatorsCalculator
                .acceptance(evidence(List.of(story("APPROVED", "aaaa bbbb", "zzzz yyyy")), 0, List.of())).rate())
                .isZero();
    }

    @Test
    @DisplayName("post-processing averages and takes the median over the finished reviews, odd count")
    void postProcessingOdd() {
        PostProcessingIndicator indicator = QualityIndicatorsCalculator.postProcessing(List.of(
                session("DISCOVERY", ENDED, ENDED.plusSeconds(600)),
                session("DISCOVERY", ENDED.plusSeconds(100), ENDED.plusSeconds(100 + 1800)),
                session("CLARIFICATION", ENDED.plusSeconds(200), ENDED.plusSeconds(200 + 7200))));

        assertThat(indicator.measured()).isEqualTo(3);
        assertThat(indicator.unmeasured()).isZero();
        assertThat(indicator.averageSeconds()).isEqualTo(3200);
        assertThat(indicator.medianSeconds()).isEqualTo(1800);
        assertThat(indicator.sessions()).extracting(r -> r.seconds()).containsExactly(7200L, 1800L, 600L);
    }

    @Test
    @DisplayName("post-processing median of an even count is the mean of the two middle values, rounded")
    void postProcessingEven() {
        PostProcessingIndicator indicator = QualityIndicatorsCalculator.postProcessing(List.of(
                session("DISCOVERY", ENDED, ENDED.plusSeconds(60)),
                session("DISCOVERY", ENDED, ENDED.plusSeconds(100)),
                session("DISCOVERY", ENDED, ENDED.plusSeconds(101)),
                session("DISCOVERY", ENDED, ENDED.plusSeconds(10_000))));

        assertThat(indicator.medianSeconds()).isEqualTo(101);
        assertThat(indicator.averageSeconds()).isEqualTo(2565);
    }

    @Test
    @DisplayName("a single finished review is its own average and median")
    void postProcessingSingle() {
        PostProcessingIndicator indicator = QualityIndicatorsCalculator
                .postProcessing(List.of(session("DISCOVERY", ENDED, ENDED.plusSeconds(90))));

        assertThat(indicator.averageSeconds()).isEqualTo(90);
        assertThat(indicator.medianSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("meetings with no finished review are counted apart and never enter the average")
    void postProcessingUnmeasured() {
        PostProcessingIndicator indicator = QualityIndicatorsCalculator.postProcessing(List.of(
                session("DISCOVERY", ENDED, null), session("DISCOVERY", ENDED, ENDED.plusSeconds(300)),
                session("DISCOVERY", ENDED, null)));

        assertThat(indicator.measured()).isEqualTo(1);
        assertThat(indicator.unmeasured()).isEqualTo(2);
        assertThat(indicator.averageSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("when no review is finished the times are null, not zero")
    void postProcessingNoneMeasured() {
        PostProcessingIndicator indicator = QualityIndicatorsCalculator
                .postProcessing(List.of(session("DISCOVERY", ENDED, null)));

        assertThat(indicator.measured()).isZero();
        assertThat(indicator.unmeasured()).isEqualTo(1);
        assertThat(indicator.averageSeconds()).isNull();
        assertThat(indicator.medianSeconds()).isNull();
    }

    @Test
    @DisplayName("a story reviewed before the meeting ended counts as zero, never negative")
    void postProcessingNeverNegative() {
        PostProcessingIndicator indicator = QualityIndicatorsCalculator
                .postProcessing(List.of(session("DISCOVERY", ENDED, ENDED.minusSeconds(500))));

        assertThat(indicator.averageSeconds()).isZero();
        assertThat(indicator.sessions().getFirst().seconds()).isZero();
    }

    @Test
    @DisplayName("clarification meetings are the sessions created as clarification, with their share of all meetings")
    void clarification() {
        var indicator = QualityIndicatorsCalculator.clarification(List.of(session("DISCOVERY", ENDED, null),
                session("CLARIFICATION", ENDED, null), session("DISCOVERY", ENDED, null),
                session("CLARIFICATION", ENDED, null)));

        assertThat(indicator.meetings()).isEqualTo(2);
        assertThat(indicator.sessions()).isEqualTo(4);
        assertThat(indicator.share()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("no clarification meeting is a zero count, with a share when other meetings were held")
    void noClarification() {
        var indicator = QualityIndicatorsCalculator.clarification(List.of(session("DISCOVERY", ENDED, null)));

        assertThat(indicator.meetings()).isZero();
        assertThat(indicator.share()).isZero();
    }

    @Test
    @DisplayName("a period with meetings has data even when nothing was decided")
    void sessionsOnly() {
        QualityIndicators indicators = QualityIndicatorsCalculator.calculate(FROM, TO,
                evidence(List.of(), 0, List.of(session("DISCOVERY", ENDED, null))));

        assertThat(indicators.hasData()).isTrue();
        assertThat(indicators.sessions()).isEqualTo(1);
        assertThat(indicators.acceptance().rate()).isNull();
        assertThat(indicators.postProcessing().averageSeconds()).isNull();
    }
}
