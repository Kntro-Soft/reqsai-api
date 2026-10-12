package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;

/**
 * The monthly token counter right after an atomic increment.
 *
 * @param used     tokens counted in the current period, this increment included
 * @param planType the plan the counter belongs to
 */
public record TokenCounterState(long used, PlanType planType) {}
