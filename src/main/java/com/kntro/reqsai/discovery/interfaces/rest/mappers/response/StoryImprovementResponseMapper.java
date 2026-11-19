package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.AppliedStoryImprovement;
import com.kntro.reqsai.discovery.application.handler.StoryImprovementProposal;
import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ApplyStoryImprovementResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryImprovementResponse;

/** Maps the improvement of a story (proposed and applied) to its response DTOs. */
public final class StoryImprovementResponseMapper {

    private StoryImprovementResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static StoryImprovementResponse toResponse(StoryImprovementProposal proposal) {
        return new StoryImprovementResponse(
                proposal.storyId(),
                toResponse(proposal.proposal()),
                proposal.pendingValues().stream().map(StoryImprovementResponseMapper::toResponse).toList(),
                proposal.fixes(),
                StoryQualityResponseMapper.toResponse(proposal.expectedQuality()));
    }

    public static ApplyStoryImprovementResponse toResponse(AppliedStoryImprovement applied) {
        return new ApplyStoryImprovementResponse(
                UserStoryResponseMapper.toResponse(applied.story(), applied.quality()),
                StoryQualityResponseMapper.toResponse(applied.quality()));
    }

    private static StoryImprovementResponse.Proposal toResponse(StoryDraft draft) {
        return new StoryImprovementResponse.Proposal(draft.title(), draft.role(), draft.action(), draft.benefit(),
                draft.acceptanceCriteria().stream()
                        .map(c -> new StoryImprovementResponse.Criterion(c.scenario(), c.given(), c.when(), c.then()))
                        .toList());
    }

    private static StoryImprovementResponse.PendingValue toResponse(StoryImprovementResult.PendingValue value) {
        return new StoryImprovementResponse.PendingValue(value.token(), value.hint());
    }
}
