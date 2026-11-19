package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks what a model proposed for a story before anyone sees it (US73), whatever the provider. It does not trust
 * the model with the limits of the story or with the contract of the pending values:
 * <ul>
 *   <li><b>Lengths</b> follow the story ({@code title} 200, {@code role}/{@code action}/{@code benefit} 500) and its
 *       criteria (scenario 200, given/when/then 1000), at most {@link #MAX_CRITERIA} criteria. A text that is too
 *       long, or a required one that is blank, is rejected, never cut: cutting could drop a token or change the
 *       meaning.</li>
 *   <li><b>Pending values</b>: a token written in the text but not declared is rejected (the analyst would not be
 *       told what to supply); a declared token that is not in the text is dropped (there is nothing to fill in);
 *       a declaration written without brackets is wrapped; a declared token without a hint gets the text between
 *       its brackets as hint. The result lists each token once, in order of appearance.</li>
 *   <li><b>Fixes</b> are trimmed, deduplicated, at most {@link #MAX_FIXES} of {@link #FIX_MAX} characters.</li>
 * </ul>
 * A rejection is an {@link UnparseableGenerationException}, so the caller can ask the model again.
 */
@Slf4j
public final class StoryImprovementNormalizer {

    public static final int MAX_CRITERIA = 20;
    static final int MAX_FIXES = 10;
    static final int FIX_MAX = 300;

    private StoryImprovementNormalizer() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static StoryImprovementResult normalize(StoryImprovementResult raw) {
        StoryDraft proposal = raw.proposal();
        if (proposal == null) {
            throw rejected("the reply has no proposal");
        }
        List<StoryDraft.CriterionDraft> criteria = new ArrayList<>();
        for (StoryDraft.CriterionDraft criterion : proposal.acceptanceCriteria()) {
            criteria.add(new StoryDraft.CriterionDraft(
                    optional(criterion.scenario(), "scenario", AcceptanceCriterion.SCENARIO_MAX),
                    required(criterion.given(), "given", AcceptanceCriterion.FIELD_MAX),
                    required(criterion.when(), "when", AcceptanceCriterion.FIELD_MAX),
                    required(criterion.then(), "then", AcceptanceCriterion.FIELD_MAX)));
        }
        if (criteria.size() > MAX_CRITERIA) {
            throw rejected("%d acceptance criteria (at most %d)".formatted(criteria.size(), MAX_CRITERIA));
        }
        StoryDraft clean = new StoryDraft(
                required(proposal.title(), "title", UserStory.TITLE_MAX),
                required(proposal.role(), "role", UserStory.FIELD_MAX),
                required(proposal.action(), "action", UserStory.FIELD_MAX),
                required(proposal.benefit(), "benefit", UserStory.FIELD_MAX),
                criteria);
        return new StoryImprovementResult(clean, pendingValues(clean, raw.pendingValues()), fixes(raw.fixes()));
    }

    private static List<StoryImprovementResult.PendingValue> pendingValues(
            StoryDraft proposal, List<StoryImprovementResult.PendingValue> declared) {
        List<String> inText = PendingValues.in(proposal);
        Map<String, String> hints = new LinkedHashMap<>();
        for (StoryImprovementResult.PendingValue value : declared) {
            String token = PendingValues.literal(value.token());
            if (token == null || !inText.contains(token)) {
                log.debug("Story improvement: dropping declared pending value '{}' that is not in the text", value.token());
                continue;
            }
            String hint = value.hint() == null || value.hint().isBlank()
                    ? PendingValues.inner(token) : value.hint().strip();
            hints.putIfAbsent(token, hint);
        }
        List<String> undeclared = inText.stream().filter(token -> !hints.containsKey(token)).toList();
        if (!undeclared.isEmpty()) {
            throw rejected("the text has pending values the reply does not declare: " + String.join(", ", undeclared));
        }
        return inText.stream().map(token -> new StoryImprovementResult.PendingValue(token, hints.get(token))).toList();
    }

    private static List<String> fixes(List<String> raw) {
        Set<String> fixes = new LinkedHashSet<>();
        for (String fix : raw) {
            if (fix == null || fix.isBlank() || fixes.size() >= MAX_FIXES) {
                continue;
            }
            String text = fix.strip();
            fixes.add(text.length() <= FIX_MAX ? text : text.substring(0, FIX_MAX - 1) + "…");
        }
        return List.copyOf(fixes);
    }

    private static String required(@Nullable String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw rejected("'" + field + "' is empty");
        }
        String text = value.strip();
        if (text.length() > max) {
            throw rejected("'%s' has %d characters (at most %d)".formatted(field, text.length(), max));
        }
        return text;
    }

    private static @Nullable String optional(@Nullable String value, String field, int max) {
        return value == null || value.isBlank() ? null : required(value, field, max);
    }

    private static UnparseableGenerationException rejected(String reason) {
        return new UnparseableGenerationException("Unusable story improvement: " + reason, null);
    }
}
