package com.kntro.reqsai.gateway.infrastructure.batch;

import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.gateway.application.service.StoryPushService;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Chunk-step processor for the push-all job: one story in, pushed to the tracker via the existing
 * {@link StoryPushService} with the context resolved once per step execution. A provider failure
 * <em>throws</em> on purpose: the step's fault-tolerant skip policy swallows it, the SkipListener
 * counts it as a failed item, and the batch moves on — the same per-item semantics the old
 * synchronous push-all endpoint had.
 */
@RequiredArgsConstructor
public class JiraStoryPushItemProcessor implements ItemProcessor<StoryView, SyncItemOutcome> {

    private final StoryPushService pushService;
    private final PushContext context;

    @Override
    public SyncItemOutcome process(StoryView story) {
        pushService.push(context, story);
        return SyncItemOutcome.SUCCEEDED;
    }
}
