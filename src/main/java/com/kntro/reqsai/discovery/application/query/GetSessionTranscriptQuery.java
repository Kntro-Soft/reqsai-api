package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** Query to retrieve the transcript text of a discovery session. */
public record GetSessionTranscriptQuery(UUID sessionId) {
}
