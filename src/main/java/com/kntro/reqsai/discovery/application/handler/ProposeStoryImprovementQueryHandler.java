package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementPort;
import com.kntro.reqsai.discovery.application.port.StoryImprovementRequest;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.StoryQualityEvaluator;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.ProposeStoryImprovementQuery;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.application.service.StoryImprovementNormalizer;
import com.kntro.reqsai.discovery.application.service.StoryQualityReader;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.StorySourceFragment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks the AI for an improved version of a story (US73) and returns it as a proposal, without changing anything.
 * <p>
 * The model is given the story, the quality characteristics it does not meet (from the stored assessment, or a
 * fresh evaluation when it was never assessed), the project's glossary and constraints nearest to it and the
 * fragments of the meetings it comes from, so the proposal stays faithful to what the client said. What the model
 * returns is checked by {@link StoryImprovementNormalizer}; an unreadable or unusable reply is asked for again once.
 * <p>
 * Behaves like the other explicit AI requests: with no model the answer is {@code REQUIREMENT_GENERATION_FAILED}
 * (422), with the monthly allowance spent it is {@code AI_QUOTA_EXCEEDED} (402) before any call is made, and the
 * tokens of the call are metered as {@link AiFeature#STORY_IMPROVEMENT}. The proposal's expected quality is the
 * rule-based evaluation of the proposal as a transient story: it costs no AI allowance and is not stored.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProposeStoryImprovementQueryHandler {

    /** How many times the model is asked when its reply is not usable. */
    private static final int MAX_ATTEMPTS = 2;
    /** Meeting fragments given to the model; the oldest are the story's own origin. */
    static final int MAX_QUOTES = 6;

    private final UserStoryRepository stories;
    private final StoryImprovementPort improvement;
    private final StoryQualityReader qualityReader;
    private final StoryQualityEvaluator evaluator;
    private final RealtimeSuggestionService contextService;
    private final AiQuotaGuard quota;

    @Transactional(readOnly = true)
    public StoryImprovementProposal handle(ProposeStoryImprovementQuery query) {
        UserStory story = stories.findByIdAndProjectId(query.storyId(), query.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(query.storyId()));
        if (!improvement.isAvailable()) {
            throw DiscoveryExceptions.requirementGenerationFailed("The AI story improvement is not available");
        }
        quota.assertAvailable();

        StoryImprovementRequest request = new StoryImprovementRequest(
                draftOf(story), unmetFindings(story), contextOf(story), sourceQuotes(story));
        StoryImprovementResult result = AiUsageScope.call(AiFeature.STORY_IMPROVEMENT, query.projectId(),
                () -> improve(request));

        StoryQualityAssessment expected = expectedQuality(story, result.proposal());
        log.info("Improvement proposed for story {} of project {}: {} fix(es), {} pending value(s), expected quality {}",
                story.getId(), query.projectId(), result.fixes().size(), result.pendingValues().size(),
                expected.getScore());
        return new StoryImprovementProposal(story.getId(), result.proposal(), result.pendingValues(), result.fixes(),
                expected);
    }

    private StoryImprovementResult improve(StoryImprovementRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return StoryImprovementNormalizer.normalize(improvement.improve(request));
            } catch (UnparseableGenerationException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw DiscoveryExceptions.requirementGenerationFailed("The improved story could not be read");
                }
                log.info("Story improvement: unusable model reply (attempt {}/{}), asking again: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
    }

    private static StoryDraft draftOf(UserStory story) {
        return new StoryDraft(story.getTitle(), story.getRole(), story.getAction(), story.getBenefit(),
                story.getAcceptanceCriteria().stream()
                        .map(c -> new StoryDraft.CriterionDraft(c.getScenario(), c.getGiven(), c.getWhen(), c.getThen()))
                        .toList());
    }

    private List<StoryImprovementRequest.Finding> unmetFindings(UserStory story) {
        StoryQualityAssessment assessment = qualityReader.read(List.of(story)).get(story.getId());
        return assessment.unmetFindings().stream()
                .map(f -> new StoryImprovementRequest.Finding(f.characteristic().name(), f.observation(), f.suggestion()))
                .toList();
    }

    private @Nullable GenerationContext contextOf(UserStory story) {
        return contextService.profileContextFor(story.getProjectId(), story.toCanonicalText()).orElse(null);
    }

    /** The quotes of the meetings the story comes from; a story from before fragments were kept has its origin quote. */
    private static List<String> sourceQuotes(UserStory story) {
        List<String> quotes = new ArrayList<>();
        for (StorySourceFragment fragment : story.getSourceFragments()) {
            if (quotes.size() < MAX_QUOTES) {
                quotes.add(fragment.getQuote());
            }
        }
        if (quotes.isEmpty() && story.getOriginQuote() != null) {
            quotes.add(story.getOriginQuote());
        }
        return quotes;
    }

    /**
     * The rule-based evaluation of the proposal, run on a story that is never stored: it keeps the priority and
     * estimate of the real one (the atomicity rule reads the estimate) and is identified as that story.
     */
    private StoryQualityAssessment expectedQuality(UserStory story, StoryDraft proposal) {
        UserStory transientStory = new UserStory(story.getProjectId(), proposal.title(), proposal.role(),
                proposal.action(), proposal.benefit(), story.getPriority(), story.getStoryPoints());
        proposal.acceptanceCriteria().forEach(c ->
                transientStory.addAcceptanceCriterion(c.scenario(), c.given(), c.when(), c.then()));
        List<QualityFinding> findings = evaluator.evaluate(transientStory);
        return StoryQualityAssessment.evaluate(story.getId(), findings, Instant.now());
    }
}
