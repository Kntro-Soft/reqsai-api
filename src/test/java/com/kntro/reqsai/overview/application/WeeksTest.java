package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.discovery.insights.WeeklyCount;
import com.kntro.reqsai.overview.application.result.WeekCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application: week window of the dashboards")
class WeeksTest {

    // Wednesday 2026-10-07: its week starts on Monday 2026-10-05.
    private static final Instant NOW = Instant.parse("2026-10-07T15:30:00Z");

    @Test
    @DisplayName("window ends with the current Monday and runs oldest to newest")
    void windowEndsWithCurrentWeek() {
        List<LocalDate> window = Weeks.window(NOW, 4);

        assertThat(window).containsExactly(
                LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-21"),
                LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-05"));
    }

    @Test
    @DisplayName("a Monday midnight UTC belongs to the week it opens")
    void mondayOpensItsWeek() {
        assertThat(Weeks.window(Instant.parse("2026-10-05T00:00:00Z"), 1)).containsExactly(LocalDate.parse("2026-10-05"));
        assertThat(Weeks.window(Instant.parse("2026-10-11T23:59:59Z"), 1)).containsExactly(LocalDate.parse("2026-10-05"));
    }

    @Test
    @DisplayName("start is midnight UTC of the first week")
    void startIsFirstMondayAtMidnight() {
        assertThat(Weeks.start(Weeks.window(NOW, 12))).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
    }

    @Test
    @DisplayName("fill gives every week a count, zero where the database had no rows")
    void fillZeroPads() {
        List<LocalDate> window = Weeks.window(NOW, 3);

        List<WeekCount> filled = Weeks.fill(window, List.of(
                new WeeklyCount(LocalDate.parse("2026-09-28"), 2),
                new WeeklyCount(LocalDate.parse("2026-10-05"), 5),
                new WeeklyCount(LocalDate.parse("2026-01-05"), 9)));

        assertThat(filled).extracting(WeekCount::weekStart).containsExactlyElementsOf(window);
        assertThat(filled).extracting(WeekCount::count).containsExactly(0L, 2L, 5L);
    }
}
