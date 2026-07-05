package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import com.kntro.reqsai.discovery.domain.model.Priority;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Shared base for LLM-backed {@link RequirementGenerationPort} adapters.
 * Contains the extraction prompt, JSON parsing, Markdown stripping, context injection,
 * and null-safe model invocation. Subclasses implement {@link #callModel(String)} and
 * {@link #modelName()} to wire a specific ChatModel.
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

            Classify each item with a "type":
            - "NEW_STORY"   — a new, standalone user story.
            - "EDGE_CASE"   — a boundary or exceptional scenario that belongs as an acceptance criterion
                              on an existing story (not a new story); include a "relatedTopic" hint and
                              put the boundary rule as EXACTLY ONE Given/When/Then entry in
                              "acceptanceCriteria".
            - "CLARIFYING_QUESTION" — the transcript is ambiguous; ask a question instead of guessing.
                                      Use the "questions" array, NOT the "stories" array.

            For EACH acceptance criterion give a concise "scenario" label (max 200 chars) in the
            transcript language; use null only if you truly cannot.

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
                  "type": "NEW_STORY | EDGE_CASE",
                  "title": "Short descriptive title (max 200 chars)",
                  "role": "User role / actor (max 500 chars)",
                  "action": "What they want to do (max 500 chars)",
                  "benefit": "Expected benefit or reason (max 500 chars)",
                  "priority": "CRITICAL | HIGH | MEDIUM | LOW",
                  "storyPoints": 1,
                  "relatedTopic": "Only for EDGE_CASE: brief topic hint (max 200 chars) or null",
                  "acceptanceCriteria": [
                    {
                      "scenario": "Brief label for this criterion in the transcript language (max 200 chars); null only if impossible",
                      "given": "Given context / precondition (max 1000 chars)",
                      "when": "When this action is performed (max 1000 chars)",
                      "then": "Then this outcome should occur (max 1000 chars)"
                    }
                  ]
                }
              ],
              "questions": [
                { "question": "Clarifying question text (max 1000 chars)" }
              ]
            }

            Transcript:
            %s
            """;

    private static final String CONTEXTUAL_EXTRACTION_PROMPT = """
            You are an expert requirements analyst specializing in agile software development.
            Use the PROJECT CONTEXT below to understand the domain and generate accurate suggestions.

            %s

            Rules:
            - Group related mentions into a single story (avoid duplicates).
            - Apply domain glossary terms where they match the conversation.
            - Use the SAME LANGUAGE as the transcript for all text fields.
            - CRITICAL: check the EXISTING USER STORIES list before emitting anything. If the
              conversation revisits, refines, extends, changes or duplicates one of those stories
              (even with different wording or in another language), do NOT create a NEW_STORY — emit
              UPDATE_STORY (or EDGE_CASE for a boundary scenario) with that story's id as
              "targetStoryId". Only emit NEW_STORY for a capability no existing story covers.
              Verbal cues that almost always mean UPDATE_STORY of an existing story (bilingual):
              "volviendo a…", "sobre lo de…", "además … debe…", "también quiero que … soporte…",
              "cambiar…", "en realidad…"; "going back to…", "also it should…", "actually…",
              "on top of that…", "let's change…". Match them to the story they refer to by meaning.
            - QUALITY BAR: if a transcript fragment is garbled, truncated, contradictory or you
              cannot form a coherent, complete user story from it, do NOT emit a suggestion. Speech
              recognition mishears words (e.g. "inicio de sesión" → "inicio de decisión"); never
              invent a requirement around an obvious mistranscription. Prefer emitting nothing over a
              nonsensical story.
            - GRANULARITY: session maintenance (keeping a user logged in), error/validation messages,
              input validations, and security constraints (encryption, rate limits, password policy)
              OF an existing capability are NOT separate stories. Emit them as EDGE_CASE (acceptance
              criterion) or UPDATE_STORY on the capability they belong to, never as a standalone
              NEW_STORY. Examples:
                · "mantener la sesión activa" → EDGE_CASE / UPDATE_STORY of the login story, not new.
                · "mostrar un mensaje de error si la contraseña es inválida" → EDGE_CASE of login.
                · "the export must be encrypted" → EDGE_CASE / UPDATE_STORY of the export story.
            - Do NOT re-suggest anything equivalent (same meaning, any wording or language) to an item
              in ALREADY SUGGESTED THIS SESSION; those are pending analyst review and repeating them
              floods the queue. This is a hard constraint, not a preference.
            - For every NEW_STORY, propose 2 to 4 acceptance criteria, each an explicit
              Given / When / Then triple in the SAME LANGUAGE as the transcript. Base them on what was
              actually said; do not fabricate. If you cannot form at least one complete Given/When/Then
              triple, return an empty "acceptanceCriteria" array rather than inventing one.
            - For every EDGE_CASE, provide EXACTLY ONE acceptance criterion in "acceptanceCriteria":
              the boundary/exceptional/validation/security rule itself, as an explicit
              Given / When / Then triple, plus the existing story it belongs to in "targetStoryId".
              Do NOT restate the parent story as an edge case.
            - For EACH acceptance criterion (NEW_STORY list and the single EDGE_CASE one), also give a
              concise "scenario" label (max 200 chars) in the SAME LANGUAGE as the transcript. Omit it
              (null) only if you truly cannot; never fabricate one.
            - CRITICAL: Return ONLY valid JSON — no markdown, no code fences, no explanation.

            Classify each item with a "type":
            - "NEW_STORY"    — a new, standalone user story not covered by any existing story in the context.
                               "targetStoryId" must be null.
            - "UPDATE_STORY" — the conversation revisits, refines, extends, changes or duplicates an
                               EXISTING user story from the list; set "targetStoryId" to that story's id
                               and write the full updated story fields.
            - "EDGE_CASE"    — a boundary, exceptional scenario, or a session-maintenance / error /
                               validation / security constraint that belongs as an acceptance criterion
                               on an existing story rather than as a new standalone story; set
                               "targetStoryId" to that story's id when you can identify it, include a
                               "relatedTopic" hint (a glossary term or a concept already mentioned in the
                               context), and put the boundary rule itself as EXACTLY ONE Given/When/Then
                               entry in "acceptanceCriteria".
            - "CLARIFYING_QUESTION" — the transcript is ambiguous; ask a question instead of guessing.
                                      Use the "questions" array, NOT the "stories" array.

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
                  "type": "NEW_STORY | UPDATE_STORY | EDGE_CASE",
                  "targetStoryId": "id of the existing story for UPDATE_STORY / EDGE_CASE, or null",
                  "title": "Short descriptive title (max 200 chars)",
                  "role": "User role / actor (max 500 chars)",
                  "action": "What they want to do (max 500 chars)",
                  "benefit": "Expected benefit or reason (max 500 chars)",
                  "priority": "CRITICAL | HIGH | MEDIUM | LOW",
                  "storyPoints": 1,
                  "relatedTopic": "Only for EDGE_CASE: glossary term or concept the edge case belongs to, or null",
                  "acceptanceCriteria": [
                    {
                      "scenario": "Brief label for this criterion in the transcript language (max 200 chars); null only if impossible",
                      "given": "Given context / precondition (max 1000 chars)",
                      "when": "When this action is performed (max 1000 chars)",
                      "then": "Then this outcome should occur (max 1000 chars)"
                    }
                  ]
                }
              ],
              "questions": [
                { "question": "Clarifying question text (max 1000 chars)" }
              ]
            }

            NEW_STORY: 2-4 acceptance criteria. EDGE_CASE: exactly one (the boundary rule).

            Recent conversation:
            %s
            """;

    private final ObjectMapper objectMapper;

    protected AbstractLlmGenerationAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Sends {@code promptText} to the concrete model and returns the raw text response. */
    protected abstract String callModel(String promptText);

    /** Name used in log messages and error details (e.g. "Gemini", "OpenAI"). */
    protected abstract String modelName();

    @Override
    public GenerationResult generate(String transcript, String language) {
        log.debug("Sending extraction prompt to {} ({} chars)", modelName(), transcript.length());
        return callAndParse(EXTRACTION_PROMPT.formatted(transcript));
    }

    @Override
    public GenerationResult generate(String transcript, String language, @Nullable GenerationContext context) {
        if (context == null) return generate(transcript, language);
        String contextBlock = buildContextBlock(context);
        log.debug("Sending contextual extraction prompt to {} ({} chars)", modelName(), transcript.length());
        return callAndParse(CONTEXTUAL_EXTRACTION_PROMPT.formatted(contextBlock, transcript));
    }

    private GenerationResult callAndParse(String promptText) {
        String json = stripMarkdown(callModel(promptText));
        log.debug("{} response ({} chars)", modelName(), json.length());
        return parseJsonResponse(json);
    }

    private static String buildContextBlock(GenerationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT: ").append(ctx.projectName()).append("\n");
        if (ctx.projectDescription() != null) {
            sb.append("Description: ").append(ctx.projectDescription()).append("\n");
        }
        if (!ctx.programmingLanguages().isEmpty() || !ctx.frameworks().isEmpty()) {
            sb.append("Tech stack: ");
            if (!ctx.programmingLanguages().isEmpty()) sb.append(String.join(", ", ctx.programmingLanguages()));
            if (!ctx.frameworks().isEmpty()) sb.append(" | ").append(String.join(", ", ctx.frameworks()));
            if (!ctx.databases().isEmpty()) sb.append(" | DB: ").append(String.join(", ", ctx.databases()));
            sb.append("\n");
        }
        if (ctx.architecture() != null && !ctx.architecture().isBlank()) {
            sb.append("Architecture: ").append(ctx.architecture()).append("\n");
        }
        if (ctx.domain() != null && !ctx.domain().isBlank()) {
            sb.append("Domain: ").append(ctx.domain()).append("\n");
        }
        if (!ctx.constraints().isEmpty()) {
            sb.append("Constraints:\n");
            ctx.constraints().forEach(c -> sb.append("- ").append(c).append("\n"));
        }
        if (!ctx.glossaryTerms().isEmpty()) {
            sb.append("Domain glossary:\n");
            ctx.glossaryTerms().forEach(g -> sb.append("- ").append(g.term()).append(": ").append(g.definition()).append("\n"));
        }
        sb.append("\nEXISTING USER STORIES (current backlog; format: id | title | as <role> I want <action> so that <benefit>):\n");
        if (ctx.existingStories().isEmpty()) {
            sb.append("- none yet\n");
        } else {
            ctx.existingStories().forEach(s -> sb.append("- ").append(s.id())
                    .append(" | ").append(s.title())
                    .append(" | as ").append(s.role())
                    .append(" I want ").append(s.action())
                    .append(" so that ").append(s.benefit())
                    .append("\n"));
        }
        if (!ctx.alreadySuggested().isEmpty()) {
            sb.append("\nALREADY SUGGESTED THIS SESSION — pending analyst review. Do NOT emit anything")
              .append(" equivalent to these (same meaning in any wording or language); they are already")
              .append(" in the queue:\n");
            ctx.alreadySuggested().forEach(t -> sb.append("- ").append(t).append("\n"));
        }
        return sb.toString().strip();
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

    protected GenerationResult parseJsonResponse(String json) {
        try {
            if (json.startsWith("[")) {
                log.debug("{} returned array instead of object — treating as no stories", modelName());
                return new GenerationResult(List.of(), List.of());
            }
            LlmResponse parsed = objectMapper.readValue(json, LlmResponse.class);

            List<GenerationResult.GeneratedStory> stories = parsed.stories() == null
                    ? List.of()
                    : parsed.stories().stream().map(this::toGeneratedStory).toList();

            List<GenerationResult.GeneratedQuestion> questions = parsed.questions() == null
                    ? List.of()
                    : parsed.questions().stream()
                    .filter(q -> q.question() != null && !q.question().isBlank())
                    .map(q -> new GenerationResult.GeneratedQuestion(q.question()))
                    .toList();

            return new GenerationResult(stories, questions);
        } catch (Exception e) {
            log.error("Failed to parse {} response: {}", modelName(), e.getMessage());
            log.debug("Full {} response was: {}", modelName(), json);
            throw DiscoveryInfrastructureExceptions.generationFailed("Invalid JSON from " + modelName() + ": " + e.getMessage(), e);
        }
    }

    private GenerationResult.GeneratedStory toGeneratedStory(LlmStory story) {
        List<GenerationResult.GeneratedCriterion> criteria = story.acceptanceCriteria() == null
                ? List.of()
                : story.acceptanceCriteria().stream().map(this::toGeneratedCriterion).toList();

        SuggestionType type = parseSuggestionType(story.type());

        return new GenerationResult.GeneratedStory(
                type,
                story.title(), story.role(), story.action(), story.benefit(),
                parsePriority(story.priority()), story.storyPoints(),
                criteria, story.relatedTopic(), parseUuid(story.targetStoryId()));
    }

    private GenerationResult.GeneratedCriterion toGeneratedCriterion(LlmCriterion criterion) {
        return new GenerationResult.GeneratedCriterion(criterion.scenario(), criterion.given(), criterion.when(), criterion.then());
    }

    protected static Priority parsePriority(String value) {
        if (value == null) return Priority.MEDIUM;
        try {
            return Priority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.MEDIUM;
        }
    }

    protected static SuggestionType parseSuggestionType(@Nullable String value) {
        if (value == null) return SuggestionType.NEW_STORY;
        try {
            return SuggestionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SuggestionType.NEW_STORY;
        }
    }

    /** Parses a UUID the LLM echoed back, tolerating null/blank/hallucinated values. */
    protected static java.util.@Nullable UUID parseUuid(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.util.UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Shared Jackson records for all LLM adapters

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmResponse(List<LlmStory> stories, @Nullable List<LlmQuestion> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmStory(@Nullable String type, String title, String role, String action, String benefit,
                               String priority, @Nullable Integer storyPoints,
                               @Nullable String relatedTopic, @Nullable String targetStoryId,
                               @Nullable List<LlmCriterion> acceptanceCriteria) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmCriterion(@Nullable String scenario, String given, String when, String then) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmQuestion(String question) {}
}
