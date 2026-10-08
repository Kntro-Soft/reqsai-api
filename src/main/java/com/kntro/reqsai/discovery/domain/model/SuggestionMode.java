package com.kntro.reqsai.discovery.domain.model;

/**
 * When the assistant analyzes a live session's conversation (US46).
 * <ul>
 *   <li>{@code AUTO}: on its own, as the transcript accrues and when the recording stops (default);</li>
 *   <li>{@code MANUAL}: only when the analyst asks for it ("Analizar ahora").</li>
 * </ul>
 */
public enum SuggestionMode {
    AUTO,
    MANUAL
}
