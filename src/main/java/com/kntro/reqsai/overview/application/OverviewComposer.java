package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.BacklogInsights;
import com.kntro.reqsai.discovery.insights.SessionInsights;
import com.kntro.reqsai.overview.application.result.AiAssistance;
import com.kntro.reqsai.overview.application.result.BacklogSummary;
import com.kntro.reqsai.overview.application.result.DiscoverySummary;
import com.kntro.reqsai.overview.application.result.SessionSummary;
import com.kntro.reqsai.overview.application.result.StoryCoverage;
import com.kntro.reqsai.overview.application.result.StoryPoints;
import com.kntro.reqsai.overview.application.result.StorySummary;

import java.time.LocalDate;
import java.util.List;

/**
 * Reshapes what Discovery publishes into the sections both dashboards share.
 */
final class OverviewComposer {

    private OverviewComposer() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    static BacklogSummary backlog(BacklogInsights insights, List<LocalDate> window) {
        return new BacklogSummary(insights.total(), insights.byStatus(), insights.byPriority(),
                new StoryPoints(insights.storyPoints(), insights.approvedStoryPoints(), insights.unestimated()),
                new StoryCoverage(insights.liveStories(), insights.withAcceptanceCriteria(), insights.withOrigin(),
                        insights.linkedToCode()),
                Weeks.fill(window, insights.createdByWeek()),
                insights.recent().stream().map(story -> new StorySummary(story.id(), story.projectId(), story.title(),
                        story.status(), story.priority(), story.createdAt())).toList());
    }

    static DiscoverySummary discovery(SessionInsights insights, List<LocalDate> window) {
        return new DiscoverySummary(
                insights.total(), insights.byStatus(), Weeks.fill(window, insights
                        .createdByWeek()),
                new AiAssistance(insights.suggestionsAccepted(), insights.suggestionsDismissed(),
                        insights.suggestionsPending(), insights.questionsAsked()),
                insights.recent().stream().map(session -> new SessionSummary(session.id(), session.projectId(),
                        session.title(), session.status(), session.createdAt())).toList());
    }
}
