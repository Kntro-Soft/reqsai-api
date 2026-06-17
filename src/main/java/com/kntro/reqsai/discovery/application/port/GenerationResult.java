package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.Priority;

import java.util.List;

/**
 * Value object returned by {@link RequirementGenerationPort} after AI extraction.
 * Contains the list of generated user stories, each with its acceptance criteria.
 */
public record GenerationResult(List<GeneratedStory> stories) {

    public record GeneratedStory(
            String title,
            String role,
            String action,
            String benefit,
            Priority priority,
            Integer storyPoints,
            List<GeneratedCriterion> acceptanceCriteria
    ) {}

    public record GeneratedCriterion(String given, String when, String then) {}
}
