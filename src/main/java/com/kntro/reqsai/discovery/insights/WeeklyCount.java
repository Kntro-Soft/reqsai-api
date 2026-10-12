package com.kntro.reqsai.discovery.insights;

import java.time.LocalDate;

/**
 * Rows created during one ISO week.
 *
 * @param weekStart
 *            the Monday (UTC) the week starts on
 * @param count
 *            how many rows were created that week
 */
public record WeeklyCount(LocalDate weekStart, long count) {
}
