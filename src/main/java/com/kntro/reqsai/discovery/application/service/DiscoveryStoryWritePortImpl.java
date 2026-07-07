package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.api.DiscoveryStoryWritePort;
import com.kntro.reqsai.discovery.api.ExternalIssueInput;
import com.kntro.reqsai.discovery.api.ImportedStory;
import com.kntro.reqsai.discovery.api.StoryDuplicateCheck;
import com.kntro.reqsai.discovery.application.command.CreateUserStoryCommand;
import com.kntro.reqsai.discovery.application.handler.CreateUserStoryCommandHandler;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.GenerationResult.GeneratedStory;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Package-private cross-context implementation of {@link DiscoveryStoryWritePort}. Transforms an external
 * tracker issue into a well-formed {@link UserStory} and creates it through the existing
 * {@link CreateUserStoryCommandHandler}, so the similarity/deduplication gate is identical to manual and
 * AI-generated creation.
 *
 * <p><strong>Transformation (two paths, documented):</strong>
 * <ol>
 *   <li><em>LLM path</em> — when {@link RequirementGenerationPort#isAvailable()} the issue's summary +
 *       plain-text description are fed to Discovery's existing generation as a short transcript; the first
 *       generated story (already role/action/benefit + acceptance criteria) is used. This is the requested
 *       behaviour: no regex parsing of Jira text.</li>
 *   <li><em>Deterministic fallback</em> — when the model is unconfigured or generation fails/returns
 *       nothing, a safe mapping is used: {@code title = summary}, a minimal valid role/action, and the
 *       description (or a default) as the benefit. This guarantees the required story fields are always
 *       satisfied so import works without an LLM.</li>
 * </ol>
 * Either way the resulting story goes through the standard create handler; a near-duplicate is reported as
 * {@link ImportedStory.Status#DUPLICATE} (nothing created), not propagated as an error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class DiscoveryStoryWritePortImpl implements DiscoveryStoryWritePort {

    private static final int TITLE_MAX = 200;
    private static final int FIELD_MAX = 500;

    private final RequirementGenerationPort generationPort;
    private final CreateUserStoryCommandHandler createUserStory;
    private final UserStoryRepository stories;
    private final EmbeddingPort embeddingPort;

    @Override
    @Transactional
    public ImportedStory importFromExternalIssue(ExternalIssueInput input) {
        GeneratedStory gen = transform(input);
        try {
            CreateUserStoryCommand command = new CreateUserStoryCommand(
                    input.projectId(), gen.title(), gen.role(), gen.action(), gen.benefit(),
                    gen.priority(), gen.storyPoints());
            UserStory saved = createUserStory.handle(command);
            if (gen.acceptanceCriteria() != null && !gen.acceptanceCriteria().isEmpty()) {
                gen.acceptanceCriteria().forEach(c ->
                        saved.addAcceptanceCriterion(c.scenario(), c.given(), c.when(), c.then()));
                stories.save(saved);
            }
            return ImportedStory.created(saved.getId());
        } catch (DomainException e) {
            if (e.error() == DiscoveryError.DUPLICATE_USER_STORY) {
                StoryDuplicateCheck match = resolveDuplicate(input.projectId(), gen);
                return ImportedStory.duplicate(match.existingStoryId(), match.similarity());
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoryDuplicateCheck checkDuplicate(ExternalIssueInput input) {
        if (!embeddingPort.isAvailable()) {
            return StoryDuplicateCheck.notDuplicate();
        }
        return resolveDuplicate(input.projectId(), transform(input));
    }

    /**
     * Finds the most similar existing story to the candidate and flags it as a duplicate when the score is
     * at/above the shared threshold. Uses the same canonical text + embedding as the create-time gate.
     */
    private StoryDuplicateCheck resolveDuplicate(java.util.UUID projectId, GeneratedStory gen) {
        if (!embeddingPort.isAvailable()) {
            return StoryDuplicateCheck.notDuplicate();
        }
        UserStory candidate = new UserStory(projectId, gen.title(), gen.role(), gen.action(),
                gen.benefit(), gen.priority(), gen.storyPoints());
        Optional<UserStoryRepository.SimilarStory> match =
                stories.findMostSimilar(projectId, embeddingPort.embed(candidate.toCanonicalText()));
        return match
                .map(s -> new StoryDuplicateCheck(s.similarity() >= UserStory.DUPLICATE_THRESHOLD,
                        s.storyId(), s.similarity()))
                .orElseGet(StoryDuplicateCheck::notDuplicate);
    }

    /** LLM transformation with a deterministic fallback that always yields a valid story. */
    private GeneratedStory transform(ExternalIssueInput input) {
        if (generationPort.isAvailable()) {
            try {
                GenerationResult result = generationPort.generate(seedTranscript(input), language(input));
                if (result != null && result.stories() != null && !result.stories().isEmpty()) {
                    return sanitize(result.stories().getFirst(), input);
                }
                log.info("Generation returned no story for imported issue '{}'; using safe fallback mapping",
                        input.summary());
            } catch (RuntimeException e) {
                log.warn("Generation failed for imported issue '{}'; using safe fallback mapping: {}",
                        input.summary(), e.getMessage());
            }
        }
        return fallback(input);
    }

    /** Feeds the LLM the issue as a tiny transcript so it produces one structured story. */
    private static String seedTranscript(ExternalIssueInput input) {
        String description = input.description() == null ? "" : input.description();
        return ("Convert the following tracker issue into a single user story.\n"
                + "Title: " + input.summary() + "\n"
                + "Description: " + description).strip();
    }

    private static String language(ExternalIssueInput input) {
        return input.language() == null || input.language().isBlank() ? "en-US" : input.language();
    }

    /**
     * Ensures an LLM-generated story satisfies the {@link UserStory} invariants (non-blank, bounded fields)
     * regardless of what the model returned, so a sparse generation never fails the create.
     */
    private static GeneratedStory sanitize(GeneratedStory gen, ExternalIssueInput input) {
        String title = clamp(nonBlank(gen.title(), input.summary()), TITLE_MAX);
        String role = clamp(nonBlank(gen.role(), "stakeholder"), FIELD_MAX);
        String action = clamp(nonBlank(gen.action(), deriveAction(input.summary())), FIELD_MAX);
        String benefit = clamp(nonBlank(gen.benefit(), deriveBenefit(input)), FIELD_MAX);
        Priority priority = gen.priority() == null ? Priority.MEDIUM : gen.priority();
        List<GenerationResult.GeneratedCriterion> criteria =
                gen.acceptanceCriteria() == null ? List.of() : gen.acceptanceCriteria();
        return new GeneratedStory(title, role, action, benefit, priority, gen.storyPoints(), criteria);
    }

    /** Deterministic safe mapping: title = summary, minimal valid role/action, description as benefit. */
    private static GeneratedStory fallback(ExternalIssueInput input) {
        String title = clamp(nonBlank(input.summary(), "Imported issue"), TITLE_MAX);
        return new GeneratedStory(
                title,
                "stakeholder",
                clamp(deriveAction(title), FIELD_MAX),
                clamp(deriveBenefit(input), FIELD_MAX),
                Priority.MEDIUM,
                null,
                List.of());
    }

    private static String deriveAction(String summary) {
        return "achieve: " + summary;
    }

    private static String deriveBenefit(ExternalIssueInput input) {
        String description = input.description() == null ? "" : input.description().strip();
        return description.isBlank() ? "the imported requirement is captured in the backlog" : description;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String clamp(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
