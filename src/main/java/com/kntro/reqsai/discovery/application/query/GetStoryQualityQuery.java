package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** The latest stored quality assessment of a story. */
public record GetStoryQualityQuery(UUID projectId, UUID storyId) {
}
