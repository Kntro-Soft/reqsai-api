package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.SessionStatsRepository.SessionStats;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;

import java.util.List;

/**
 * What a session link shows: the session as it stands, the latest transcript, who spoke, the suggestions
 * still waiting for a decision and the stories the session produced.
 */
public record SharedSession(
        ShareLink link,
        String projectName,
        DiscoverySession session,
        SessionStats stats,
        List<TranscriptSegment> segments,
        long totalSegments,
        SessionSpeakerService.Overview speakers,
        List<Suggestion> suggestions,
        List<UserStory> stories) {
}
