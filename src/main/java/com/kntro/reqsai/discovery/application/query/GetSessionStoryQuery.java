package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Query to retrieve a single user story in the context of the session that generated it. */
public record GetSessionStoryQuery(UUID sessionId, UUID storyId) {
}
