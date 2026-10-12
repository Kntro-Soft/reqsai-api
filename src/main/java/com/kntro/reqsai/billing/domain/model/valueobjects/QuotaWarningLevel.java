package com.kntro.reqsai.billing.domain.model.valueobjects;

/**
 * How much of the monthly token allowance an organization has spent, in the steps it is warned at.
 * <ul>
 *   <li>{@link #NONE} below 80 %</li>
 *   <li>{@link #WARNING_80} from 80 %</li>
 *   <li>{@link #WARNING_90} from 90 %</li>
 *   <li>{@link #EXHAUSTED} from 100 %: AI generation stops, reading and editing do not</li>
 * </ul>
 */
public enum QuotaWarningLevel {

    NONE(0),
    WARNING_80(80),
    WARNING_90(90),
    EXHAUSTED(100);

    private final int percent;

    QuotaWarningLevel(int percent) {
        this.percent = percent;
    }

    /** Percentage of the allowance at which this level starts; 0 for {@link #NONE}. */
    public int percent() {
        return percent;
    }

    /** The highest level {@code used} has reached against {@code limit}. */
    public static QuotaWarningLevel of(long used, long limit) {
        if (limit <= 0) {
            return NONE;
        }
        QuotaWarningLevel reached = NONE;
        for (QuotaWarningLevel level : values()) {
            if (level != NONE && used * 100 >= limit * level.percent) {
                reached = level;
            }
        }
        return reached;
    }

    /** The level stored as a percentage, or {@link #NONE} for anything unknown. */
    public static QuotaWarningLevel fromPercent(int percent) {
        QuotaWarningLevel found = NONE;
        for (QuotaWarningLevel level : values()) {
            if (level.percent <= percent) {
                found = level;
            }
        }
        return found;
    }
}
