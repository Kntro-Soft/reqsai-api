package com.kntro.reqsai.overview.application.result;

import java.util.List;
import java.util.Map;

/**
 * Discovery activity of a project or of every project a caller may see.
 *
 * @param sessions
 *            sessions of any status
 * @param byStatus
 *            sessions per status, every status present
 * @param createdByWeek
 *            sessions created per week over the window, every week present
 * @param assistance
 *            how the analysts treated the copilot's proposals
 * @param recent
 *            the newest sessions (empty on the organization page)
 */
public record DiscoverySummary(long sessions, Map<String, Long> byStatus, List<WeekCount> createdByWeek,
        AiAssistance assistance, List<SessionSummary> recent) {
}
