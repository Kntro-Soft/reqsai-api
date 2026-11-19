package com.kntro.reqsai.discovery.interfaces.rest.mappers.request;

import com.kntro.reqsai.discovery.application.command.ApplyStoryImprovementCommand;
import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.ApplyStoryImprovementRequest;

import java.util.UUID;

/** Maps the request to apply an improved story to its application command. */
public final class StoryImprovementRequestMapper {

    private StoryImprovementRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ApplyStoryImprovementCommand toCommand(UUID projectId, UUID storyId,
                                                         ApplyStoryImprovementRequest request) {
        return new ApplyStoryImprovementCommand(projectId, storyId, new StoryDraft(
                request.title().strip(), request.role().strip(), request.action().strip(), request.benefit().strip(),
                request.acceptanceCriteria().stream()
                        .map(c -> new StoryDraft.CriterionDraft(c.scenario(), c.given().strip(), c.when().strip(),
                                c.then().strip()))
                        .toList()));
    }
}
