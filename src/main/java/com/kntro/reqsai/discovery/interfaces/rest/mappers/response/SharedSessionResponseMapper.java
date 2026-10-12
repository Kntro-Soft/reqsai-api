package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.SharedSession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedSessionResponse;

/** Maps what a session link shows to its response DTO. */
public final class SharedSessionResponseMapper {

    private SharedSessionResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static SharedSessionResponse toResponse(SharedSession shared) {
        var session = shared.session();
        return new SharedSessionResponse(
                shared.projectName(),
                shared.link().getAccess().name(),
                shared.link().getExpiresAt(),
                DiscoverySessionResponseMapper.toResponse(session, shared.stats()),
                shared.segments().stream()
                        .map(segment -> TranscriptSegmentResponseMapper.toResponse(segment, session))
                        .toList(),
                shared.totalSegments(),
                SessionSpeakerResponseMapper.toResponse(
                        session.getId(), shared.speakers().roster(), shared.speakers().overlaps()),
                shared.suggestions().stream().map(SuggestionResponseMapper::toResponse).toList(),
                shared.stories().stream().map(UserStoryResponseMapper::toResponse).toList());
    }
}
