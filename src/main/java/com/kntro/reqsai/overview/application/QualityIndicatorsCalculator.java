package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.DecidedStory;
import com.kntro.reqsai.discovery.insights.HeldSession;
import com.kntro.reqsai.discovery.insights.QualityEvidence;
import com.kntro.reqsai.overview.application.result.AcceptanceIndicator;
import com.kntro.reqsai.overview.application.result.ClarificationIndicator;
import com.kntro.reqsai.overview.application.result.PostProcessingIndicator;
import com.kntro.reqsai.overview.application.result.QualityIndicators;
import com.kntro.reqsai.overview.application.result.SessionReview;
import com.kntro.reqsai.overview.domain.quality.TextSimilarity;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The formulas of the quality indicators (see {@code docs/QUALITY_INDICATORS.md}), over the evidence Discovery
 * publishes. Pure: no clock, no I/O.
 */
final class QualityIndicatorsCalculator {

    /**
     * An accepted story whose final text is at least this similar to the text the AI proposed was accepted without
     * major rewriting: at most one character in five changed.
     */
    static final double SIMILARITY_THRESHOLD = 0.80;

    private static final String REJECTED = "REJECTED";
    private static final String CLARIFICATION = "CLARIFICATION";

    private QualityIndicatorsCalculator() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    static QualityIndicators calculate(LocalDate from, LocalDate to, QualityEvidence evidence) {
        AcceptanceIndicator acceptance = acceptance(evidence);
        PostProcessingIndicator postProcessing = postProcessing(evidence.sessions());
        ClarificationIndicator clarification = clarification(evidence.sessions());
        long sessions = evidence.sessions().size();
        boolean hasData = sessions > 0 || acceptance.decided() + acceptance.withoutBaseline() > 0;
        return new QualityIndicators(from, to, hasData, sessions, acceptance, postProcessing, clarification);
    }

    /** Decided stories split into rejected, accepted as generated and accepted but rewritten. */
    static AcceptanceIndicator acceptance(QualityEvidence evidence) {
        long rejected = 0;
        long asGenerated = 0;
        long rewritten = 0;
        for (DecidedStory story : evidence.decidedStories()) {
            if (REJECTED.equals(story.status())) {
                rejected++;
            } else if (keptAsGenerated(story)) {
                asGenerated++;
            } else {
                rewritten++;
            }
        }
        return new AcceptanceIndicator(SIMILARITY_THRESHOLD, evidence.decidedStories().size(),
                asGenerated + rewritten, asGenerated, rewritten, rejected, evidence.decidedWithoutBaseline());
    }

    /** The tolerance only absorbs floating-point error at the exact threshold. */
    private static boolean keptAsGenerated(DecidedStory story) {
        return TextSimilarity.similarity(story.generatedText(), story.currentText()) + 1e-9 >= SIMILARITY_THRESHOLD;
    }

    /** Average and median of the time between the end of each meeting and the end of its review. */
    static PostProcessingIndicator postProcessing(List<HeldSession> sessions) {
        List<SessionReview> reviews = new ArrayList<>();
        for (HeldSession session : sessions) {
            if (session.reviewFinishedAt() != null) {
                long seconds = Math.max(0, Duration.between(session.endedAt(), session.reviewFinishedAt()).getSeconds());
                reviews.add(new SessionReview(session.sessionId(), session.title(), session.purpose(),
                        session.endedAt(), seconds));
            }
        }
        long unmeasured = sessions.size() - reviews.size();
        if (reviews.isEmpty()) {
            return new PostProcessingIndicator(0, unmeasured, null, null, List.of());
        }
        long[] sorted = reviews.stream().mapToLong(SessionReview::seconds).sorted().toArray();
        long total = 0;
        for (long seconds : sorted) {
            total += seconds;
        }
        long average = Math.round((double) total / sorted.length);
        long middle = sorted.length / 2;
        long median = sorted.length % 2 == 1 ? sorted[(int) middle]
                : Math.round((sorted[(int) middle - 1] + sorted[(int) middle]) / 2.0);
        reviews.sort(Comparator.comparing(SessionReview::endedAt).reversed());
        return new PostProcessingIndicator(reviews.size(), unmeasured, average, median, reviews);
    }

    static ClarificationIndicator clarification(List<HeldSession> sessions) {
        long clarifications = sessions.stream().filter(session -> CLARIFICATION.equals(session.purpose())).count();
        return new ClarificationIndicator(clarifications, sessions.size());
    }
}
