package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.WeeklyCount;
import com.kntro.reqsai.overview.application.result.WeekCount;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The window of weeks the dashboards chart. Weeks start on Monday in UTC,
 * matching how the databases groups them, and the window always ends with the
 * current week so a chart reads left (oldest) to right (now).
 */
final class Weeks {

    private Weeks() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * The Mondays of the {@code count} weeks ending with the week of {@code now},
     * oldest first.
     */
    static List<LocalDate> window(Instant now, int count) {
        LocalDate current = now.atOffset(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> weeks = new ArrayList<>(count);
        for (int i = count - 1; i >= 0; i--) {
            weeks.add(current.minusWeeks(i));
        }
        return List.copyOf(weeks);
    }

    /** The first instant of the window. */
    static Instant start(List<LocalDate> window) {
        return window.getFirst().atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /**
     * One entry per week of the window, with zero for the weeks the database had no
     * rows for.
     */
    static List<WeekCount> fill(List<LocalDate> window, List<WeeklyCount> counted) {
        Map<LocalDate, Long> byWeek = new HashMap<>();
        counted.forEach(week -> byWeek.merge(week.weekStart(), week.count(), Long::sum));
        return window.stream().map(week -> new WeekCount(week, byWeek.getOrDefault(week, 0L))).toList();
    }
}
