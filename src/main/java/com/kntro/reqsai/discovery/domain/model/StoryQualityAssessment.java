package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The latest quality assessment of a story (US68): an overall score from 0 to 100 plus one
 * {@link QualityFinding} per {@link QualityCharacteristic}. There is one per story; evaluating the story
 * again replaces its content, so the analyst always sees the current state, not a history.
 * <p>
 * The overall score is the weighted average of the findings' scores, computed here so every evaluator
 * (rule-based today, anything else later) is scored the same way.
 */
@Entity
@Table(name = "story_quality_assessments")
@Getter
public class StoryQualityAssessment extends AggregateRoot {

    public static final int MAX_SCORE = QualityFinding.MAX_SCORE;

    @Column(name = "story_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID storyId;

    @Column(name = "score", nullable = false)
    private int score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb", nullable = false)
    private List<QualityFinding> findings = new ArrayList<>();

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected StoryQualityAssessment() {
        super();
    }

    private StoryQualityAssessment(UUID storyId) {
        super();
        this.storyId = Assert.notNull(storyId, "storyId");
    }

    /** The first assessment of a story. */
    public static StoryQualityAssessment evaluate(UUID storyId, List<QualityFinding> findings, Instant evaluatedAt) {
        StoryQualityAssessment assessment = new StoryQualityAssessment(storyId);
        assessment.reevaluate(findings, evaluatedAt);
        return assessment;
    }

    /** Replaces the assessment with a fresh evaluation of the same story. */
    public void reevaluate(List<QualityFinding> findings, Instant evaluatedAt) {
        Assert.notNull(findings, "findings");
        Assert.notNull(evaluatedAt, "evaluatedAt");
        Assert.isTrue(coversEveryCharacteristicOnce(findings), "findings",
                "there must be exactly one finding per quality characteristic");
        this.findings = new ArrayList<>(findings);
        this.score = overallScore(findings);
        this.evaluatedAt = evaluatedAt;
    }

    /** Weighted average of the findings' scores, rounded to the nearest integer. */
    public static int overallScore(List<QualityFinding> findings) {
        int weights = 0;
        int weighted = 0;
        for (QualityFinding finding : findings) {
            int weight = finding.characteristic().weight();
            weights += weight;
            weighted += finding.score() * weight;
        }
        return weights == 0 ? 0 : Math.round((float) weighted / weights);
    }

    /** The findings that still need work, in evaluation order. */
    public List<QualityFinding> unmetFindings() {
        return findings.stream().filter(finding -> !finding.met()).toList();
    }

    /** Read-only view of the findings. */
    public List<QualityFinding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    private static boolean coversEveryCharacteristicOnce(List<QualityFinding> findings) {
        EnumSet<QualityCharacteristic> seen = EnumSet.noneOf(QualityCharacteristic.class);
        for (QualityFinding finding : findings) {
            if (!seen.add(finding.characteristic())) {
                return false;
            }
        }
        return seen.size() == QualityCharacteristic.values().length;
    }
}
