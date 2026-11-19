package com.kntro.reqsai.discovery.domain.model;

/**
 * What a meeting is for (US71).
 * <ul>
 *   <li>{@code DISCOVERY}: a requirements-elicitation meeting (default);</li>
 *   <li>{@code CLARIFICATION}: a follow-up meeting held to clarify requirements already captured.</li>
 * </ul>
 */
public enum SessionPurpose {
    DISCOVERY,
    CLARIFICATION
}
