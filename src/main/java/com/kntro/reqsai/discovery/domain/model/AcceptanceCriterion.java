package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * A single acceptance criterion attached to a {@link UserStory}.
 * Always expressed in Given / When / Then format.
 * {@code scenario} is an optional label set manually;
 * AI-generated criteria leave it {@code null}.
 *
 * Non-root entity: no repository, always loaded/saved through {@link UserStory}.
 */
@Entity
@Table(name = "acceptance_criteria")
@Getter
public class AcceptanceCriterion extends AuditableEntity {

    private static final int FIELD_MAX = 1000;
    private static final int SCENARIO_MAX = 200;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false, updatable = false)
    private UserStory story;

    @Column(name = "scenario", length = SCENARIO_MAX)
    private @Nullable String scenario;

    @Column(name = "given_text", nullable = false, length = FIELD_MAX)
    private String given;

    @Column(name = "when_text", nullable = false, length = FIELD_MAX)
    private String when;

    @Column(name = "then_text", nullable = false, length = FIELD_MAX)
    private String then;

    /** Required by JPA — do not use directly. */
    protected AcceptanceCriterion() {
        super();
    }

    /** Package-private: only {@link UserStory} may create criteria. */
    AcceptanceCriterion(UserStory story, @Nullable String scenario, String given, String when, String then) {
        super();
        this.story    = Assert.notNull(story, "story");
        this.scenario = scenario == null ? null : Assert.maxLength(scenario.strip(), "scenario", SCENARIO_MAX);
        this.given    = Assert.maxLength(Assert.notBlank(given, "given"), "given", FIELD_MAX);
        this.when     = Assert.maxLength(Assert.notBlank(when,  "when"),  "when",  FIELD_MAX);
        this.then     = Assert.maxLength(Assert.notBlank(then,  "then"),  "then",  FIELD_MAX);
    }

    /** Package-private: called by {@link UserStory#updateAcceptanceCriterion}. */
    void update(@Nullable String scenario, String given, String when, String then) {
        this.scenario = scenario == null ? null : Assert.maxLength(scenario.strip(), "scenario", SCENARIO_MAX);
        this.given    = Assert.maxLength(Assert.notBlank(given, "given"), "given", FIELD_MAX);
        this.when     = Assert.maxLength(Assert.notBlank(when,  "when"),  "when",  FIELD_MAX);
        this.then     = Assert.maxLength(Assert.notBlank(then,  "then"),  "then",  FIELD_MAX);
    }
}
