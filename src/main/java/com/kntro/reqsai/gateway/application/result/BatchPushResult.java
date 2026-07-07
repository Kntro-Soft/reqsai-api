package com.kntro.reqsai.gateway.application.result;

import java.util.List;

/** Aggregate result of a push-all: per-story results plus pushed/failed counts. */
public record BatchPushResult(List<StoryPushResult> results, int pushed, int failed) {

    public static BatchPushResult of(List<StoryPushResult> results) {
        int pushed = (int) results.stream().filter(StoryPushResult::isSuccess).count();
        return new BatchPushResult(results, pushed, results.size() - pushed);
    }
}
