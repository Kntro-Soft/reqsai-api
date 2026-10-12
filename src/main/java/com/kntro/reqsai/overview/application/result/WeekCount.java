package com.kntro.reqsai.overview.application.result;

import java.time.LocalDate;

/** Rows created in the week starting on {@code weekStart} (a Monday, UTC). */
public record WeekCount(LocalDate weekStart, long count) {
}
