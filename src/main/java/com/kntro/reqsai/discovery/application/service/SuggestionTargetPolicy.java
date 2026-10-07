package com.kntro.reqsai.discovery.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether an {@code UPDATE_STORY} / {@code EDGE_CASE} draft really belongs to the story it is
 * attached to, from the cosine similarity between the draft and that story.
 *
 * <p>The model sees part of the backlog in its prompt and tends to attach a new capability of the same
 * domain to the nearest-sounding listed story (a waitlist onto "Registro de menores como dependientes", a
 * delivery fee onto "Mostrar productos disponibles en la bodega más cercana"). Accepting such a draft
 * would append an unrelated criterion to that story. A target is kept only when both hold:
 * <ul>
 *   <li>its similarity clears {@code discovery.realtime.target-similarity-floor}, and</li>
 *   <li>it is the closest story, or within {@code discovery.realtime.target-similarity-margin} of it.</li>
 * </ul>
 *
 * <p>Calibrated on production embeddings ({@code text-embedding-3-small}, candidate text
 * {@code "<title>. As <role>, I want to <action>, so that <benefit>."}):
 * <ul>
 *   <li>Refinements of the right story scored 0.62–0.82 and were the closest story or within 0.05 of
 *       it: penalty on "Cancelar una cita" 0.70; anticipation window on "Reserva de citas" 0.69; copay on
 *       "Cálculo de tarifas" 0.82.</li>
 *   <li>The wrong targets seen in production scored 0.40–0.60, or sat far below the closest story:
 *       waitlist → dependants 0.49; rescheduling → insurance fees 0.48; delivery fee → nearest store
 *       0.40; courier → out-of-stock 0.50.</li>
 * </ul>
 * Drafts that are semantically adjacent to their target (a waitlist on the booking story, 0.70) are left
 * to the prompt, which only allows a target for the same capability.
 */
@Component
public class SuggestionTargetPolicy {

    private final double floor;
    private final double margin;

    public SuggestionTargetPolicy(
            @Value("${discovery.realtime.target-similarity-floor:0.60}") double floor,
            @Value("${discovery.realtime.target-similarity-margin:0.05}") double margin) {
        this.floor = floor;
        this.margin = margin;
    }

    /** Minimum draft↔target similarity for any attachment, including the nearest-story fallback. */
    public double floor() {
        return floor;
    }

    /**
     * Whether a draft may stay attached to a story it scores {@code targetSimilarity} against, when the
     * closest story of the project scores {@code bestSimilarity}.
     */
    public boolean accepts(double targetSimilarity, double bestSimilarity) {
        return targetSimilarity >= floor && targetSimilarity >= bestSimilarity - margin;
    }
}
