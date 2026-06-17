package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import com.kntro.reqsai.discovery.domain.model.Priority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Shared base for LLM-backed {@link RequirementGenerationPort} adapters.
 * Contains the extraction prompt, JSON parsing, Markdown stripping, and null-safe
 * model invocation, so individual adapters only wire the specific model type.
 */
@Slf4j
abstract class AbstractLlmGenerationAdapter implements RequirementGenerationPort {

    static final String EXTRACTION_PROMPT = """
            You are an expert requirements analyst specializing in agile software development.
            Analyze the following requirements meeting transcript and extract user stories.

            Rules:
            - Group related mentions into a single story (avoid duplicates).
            - Use the SAME LANGUAGE as the transcript for all text fields.
            - CRITICAL: Return ONLY valid JSON — no markdown, no code fences, no explanation.

            Priority mapping (based on context and language cues):
            - CRITICAL: explicit musts, "debe", "necesita", "es imprescindible", "must", "need", "required"
            - HIGH: important needs, "quiere", "importante", "should", "want"
            - MEDIUM: desirable features, "podría", "sería bueno", "could", "nice to have"
            - LOW: implied or mentioned in passing

            Story points (based on apparent complexity):
            - 1: trivial (hours)  |  2: simple (1 day)  |  3: moderate (2-3 days)
            - 5: complex (1 week) |  8: very complex (2+ weeks) | 13: epic (must be split)

            Return ONLY this JSON structure:
            {
              "stories": [
                {
                  "title": "Short descriptive title (max 200 chars)",
                  "role": "User role / actor (max 500 chars)",
                  "action": "What they want to do (max 500 chars)",
                  "benefit": "Expected benefit or reason (max 500 chars)",
                  "priority": "CRITICAL | HIGH | MEDIUM | LOW",
                  "storyPoints": 1,
                  "acceptanceCriteria": [
                    {
                      "given": "Given context / precondition",
                      "when": "When this action is performed",
                      "then": "Then this outcome should occur"
                    }
                  ]
                }
              ]
            }

            Transcript:
            %s
            """;

    private final ObjectMapper objectMapper;

    protected AbstractLlmGenerationAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected String callAndExtractText(ChatModel model, String promptText) {
        var result = model.call(new Prompt(promptText)).getResult();
        String text = result != null ? result.getOutput().getText() : null;
        if (text == null || text.isBlank()) {
            throw DiscoveryInfrastructureExceptions.generationFailed("Empty response from AI model");
        }
        return text;
    }

    /** Strips optional Markdown code fences that some models wrap around JSON. */
    protected static String stripMarkdown(String text) {
        String s = text.strip();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.strip();
    }

    protected GenerationResult parseJsonResponse(String json, String modelName) {
        try {
            LlmResponse parsed = objectMapper.readValue(json, LlmResponse.class);
            if (parsed.stories() == null) return new GenerationResult(List.of());
            List<GenerationResult.GeneratedStory> stories = parsed.stories().stream()
                    .map(this::toGeneratedStory)
                    .toList();
            return new GenerationResult(stories);
        } catch (Exception e) {
            log.error("Failed to parse {} response: {}", modelName, e.getMessage());
            log.debug("Full {} response was: {}", modelName, json);
            throw DiscoveryInfrastructureExceptions.generationFailed("Invalid JSON from " + modelName + ": " + e.getMessage(), e);
        }
    }

    private GenerationResult.GeneratedStory toGeneratedStory(LlmStory story) {
        List<GenerationResult.GeneratedCriterion> criteria = story.acceptanceCriteria() == null
                ? List.of()
                : story.acceptanceCriteria().stream()
                .map(this::toGeneratedCriterion)
                .toList();
        return new GenerationResult.GeneratedStory(story.title(), story.role(), story.action(), story.benefit(), parsePriority(story.priority()), story.storyPoints(), criteria);
    }

    private GenerationResult.GeneratedCriterion toGeneratedCriterion(LlmCriterion criterion) {
        return new GenerationResult.GeneratedCriterion(criterion.given(), criterion.when(), criterion.then());
    }

    protected static Priority parsePriority(String value) {
        if (value == null) return Priority.MEDIUM;
        try {
            return Priority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.MEDIUM;
        }
    }

    // Shared Jackson records for all LLM adapters

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmResponse(List<LlmStory> stories) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmStory(String title, String role, String action, String benefit, String priority, Integer storyPoints, List<LlmCriterion> acceptanceCriteria) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmCriterion(String given, String when, String then) {}
}
