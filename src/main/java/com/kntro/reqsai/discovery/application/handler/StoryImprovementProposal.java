package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;

import java.util.List;
import java.util.UUID;

/**
 * What the AI proposes for a story (US73). Nothing here is stored: {@code expectedQuality} is the rule-based
 * evaluation of the proposal as a transient story, so it is never persisted.
 *
 * @param storyId         the story the proposal is for (unchanged)
 * @param proposal        the improved story
 * @param pendingValues   the {@code [[...]]} tokens in it, each with the question that tells the analyst what to supply
 * @param fixes           one short sentence per change made
 * @param expectedQuality the quality the proposal would have, evaluated as it is (tokens included)
 */
public record StoryImprovementProposal(
        UUID storyId,
        StoryDraft proposal,
        List<StoryImprovementResult.PendingValue> pendingValues,
        List<String> fixes,
        StoryQualityAssessment expectedQuality
) {
}
