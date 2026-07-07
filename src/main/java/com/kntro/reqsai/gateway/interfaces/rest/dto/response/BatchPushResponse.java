package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Aggregate result of a push-all: per-story results plus pushed/failed counts. */
@Schema(description = "Result of pushing all project stories to Jira")
public record BatchPushResponse(List<JiraPushResultResponse> results, int pushed, int failed) {}
