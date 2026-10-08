package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Query for the diarized speakers of a session and its overlapping-speech stretches, scoped to a project. */
public record ListSessionSpeakersQuery(UUID projectId, UUID sessionId) {
}
