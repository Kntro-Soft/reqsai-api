package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.SendAssistantMessageCommand;
import com.kntro.reqsai.discovery.application.port.AssistantMessageRepository;
import com.kntro.reqsai.discovery.application.port.AssistantReply;
import com.kntro.reqsai.discovery.application.port.BacklogOverview;
import com.kntro.reqsai.discovery.application.port.ChatTurn;
import com.kntro.reqsai.discovery.application.port.CodeVerification;
import com.kntro.reqsai.discovery.application.port.CodeVerificationPort.SubjectKind;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.StoryFilter;
import com.kntro.reqsai.discovery.application.service.CodeVerificationService;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.application.service.SuggestionCreationService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.AssistantMessage;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.shared.application.ai.AiFeature;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiUsageScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles one message of the assistant chat, with or without a live session:
 * <ol>
 *   <li>the model reads the message with the project context and a backlog overview, answers a
 *       question, and restates any requirement it asks for ({@link RequirementGenerationPort#converse});</li>
 *   <li>a requirement goes through the regular contextual extraction and suggestion pipeline (dedup,
 *       targeting existing stories), creating suggestions that belong to the project but to no session;</li>
 *   <li>both messages are stored, the reply linked to the suggestions it raised.</li>
 * </ol>
 * The analyst reviews those suggestions from the chat, like any other (accept, edit, dismiss).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendAssistantMessageCommandHandler {

    /** Earlier messages given to the model so follow-up questions make sense. */
    private static final int HISTORY_MESSAGES = 10;
    private static final int OVERVIEW_STORIES = 60;
    private static final int OVERVIEW_SESSIONS = 5;
    /** How many times the model is asked again when its reply is not the JSON contract. */
    private static final int MAX_ATTEMPTS = 2;
    private static final String DEFAULT_LANGUAGE = "es";

    private final AssistantMessageRepository messages;
    private final RequirementGenerationPort generation;
    private final RealtimeSuggestionService contextService;
    private final SuggestionCreationService suggestionCreation;
    private final SuggestionRepository suggestions;
    private final UserStoryRepository stories;
    private final DiscoverySessionRepository sessions;
    private final AiQuotaGuard quota;
    private final CodeVerificationService codeVerification;

    /**
     * Fails with {@code AI_QUOTA_EXCEEDED} before anything is stored when the monthly AI allowance is
     * spent; the chat's reading side (history) is unaffected.
     */
    @Transactional
    public AssistantExchange handle(SendAssistantMessageCommand cmd) {
        if (!generation.isAvailable()) {
            throw DiscoveryExceptions.requirementGenerationFailed("The AI assistant is not available");
        }
        quota.assertAvailable();
        return AiUsageScope.call(AiFeature.CHAT, cmd.projectId(), () -> answer(cmd));
    }

    private AssistantExchange answer(SendAssistantMessageCommand cmd) {
        UUID projectId = cmd.projectId();
        List<ChatTurn> history = messages.findLatestByProjectId(projectId, HISTORY_MESSAGES).stream()
                .map(m -> new ChatTurn(m.getRole(), m.getContent()))
                .toList();
        AssistantMessage question = messages.save(AssistantMessage.fromAnalyst(projectId, cmd.content()));

        List<Suggestion> pendingChat = suggestions.findAllChatSuggestionsByProjectIdAndStatus(
                projectId, SuggestionStatus.PENDING);
        GenerationContext context = contextService.contextFor(projectId, question.getContent(), pendingChat)
                .orElseThrow(() -> DiscoveryExceptions.requirementGenerationFailed(
                        "Project " + projectId + " has no context to answer from"));

        AssistantReply reply = converse(question.getContent(), history, context, overview(projectId));
        List<Suggestion> created = List.of();
        if (reply.hasRequirement()) {
            GenerationResult result = generation.generate(reply.requirement(), requirementLanguage(reply), context);
            created = suggestionCreation.createSuggestions(result, null, projectId);
        }

        String language = requirementLanguage(reply);
        CodeVerification deep = reply.searchCode() ? searchCode(projectId, question.getContent(), language) : null;
        List<UUID> createdIds = created.stream().map(Suggestion::getId).toList();
        AssistantMessage answer = messages.save(deep == null
                ? AssistantMessage.fromAssistant(projectId, replyText(reply, created), createdIds)
                : AssistantMessage.fromCodeSearch(projectId, deepReplyText(deep, reply, created, language),
                        createdIds, deep.references()));
        log.info("Assistant chat for project {}: requirement={}, {} suggestion(s), deep code search={}",
                projectId, reply.hasRequirement(), created.size(), deep != null);
        return new AssistantExchange(new AssistantChatEntry(question, List.of()),
                new AssistantChatEntry(answer, created));
    }

    /**
     * The model said the index does not answer a question about the code: search the pure source code, within
     * the verification budget. Fails open: without a ready repository, a model, or in time, the chat keeps the
     * model's own short reply.
     */
    private @Nullable CodeVerification searchCode(UUID projectId, String question, String language) {
        try {
            if (!codeVerification.hasReadyRepository(projectId) || !codeVerification.isAvailable()) {
                return null;
            }
            return codeVerification.verify(projectId, question, SubjectKind.QUESTION, language,
                    codeVerification.budget());
        } catch (RuntimeException e) {
            log.info("Assistant chat for project {}: the deep code search failed, answering without it: {}",
                    projectId, e.getMessage());
            return null;
        }
    }

    /** The answer found in the code, said to come from the source code, plus the note of a requirement raised. */
    private static String deepReplyText(CodeVerification deep, AssistantReply reply, List<Suggestion> created,
                                        String language) {
        boolean english = language.toLowerCase().startsWith("en");
        StringBuilder text = new StringBuilder(deep.note().isBlank() ? reply.reply().strip() : deep.note());
        text.append("\n\n").append(english
                ? "I searched the source code directly to answer this."
                : "Lo busqué directamente en el código fuente para responderte.");
        if (reply.hasRequirement() && created.isEmpty()) {
            text.append("\n\n").append(english
                    ? "I did not raise a new suggestion: it is already covered by the backlog or by a suggestion awaiting review."
                    : "No generé una sugerencia nueva: ya está cubierto en el backlog o en una sugerencia pendiente de revisión.");
        }
        return text.toString();
    }

    private static String requirementLanguage(AssistantReply reply) {
        return reply.language() == null || reply.language().isBlank() ? DEFAULT_LANGUAGE : reply.language();
    }

    private AssistantReply converse(String message, List<ChatTurn> history, GenerationContext context,
                                    BacklogOverview overview) {
        for (int attempt = 1; ; attempt++) {
            try {
                return generation.converse(message, history, context, overview);
            } catch (UnparseableGenerationException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw DiscoveryExceptions.requirementGenerationFailed("The assistant reply could not be read");
                }
                log.info("Assistant chat: unparseable model reply (attempt {}/{}), asking again: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
    }

    /**
     * The model's reply, or a short note when it gave none or when a requirement produced no suggestion
     * (the pipeline found it already covered by the backlog or by a pending suggestion).
     */
    private static String replyText(AssistantReply reply, List<Suggestion> created) {
        boolean english = reply.language() != null && reply.language().toLowerCase().startsWith("en");
        StringBuilder text = new StringBuilder(reply.reply().strip());
        if (reply.hasRequirement() && created.isEmpty()) {
            if (!text.isEmpty()) text.append("\n\n");
            text.append(english
                    ? "I did not raise a new suggestion: it is already covered by the backlog or by a suggestion awaiting review."
                    : "No generé una sugerencia nueva: ya está cubierto en el backlog o en una sugerencia pendiente de revisión.");
        } else if (text.isEmpty()) {
            text.append(english
                    ? "I prepared a suggestion for you to review."
                    : "Preparé una sugerencia para que la revises.");
        }
        return text.toString();
    }

    private BacklogOverview overview(UUID projectId) {
        Map<StoryStatus, Long> byStatus = new EnumMap<>(StoryStatus.class);
        long total = 0;
        for (StoryStatus status : StoryStatus.values()) {
            long count = stories.findAllByProjectId(projectId,
                    new StoryFilter(null, status, null, null, null), PageRequest.of(0, 1)).getTotalElements();
            if (count > 0) byStatus.put(status, count);
            total += count;
        }
        List<BacklogOverview.StoryLine> newest = stories.findAllByProjectId(projectId,
                        PageRequest.of(0, OVERVIEW_STORIES, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(s -> new BacklogOverview.StoryLine(s.getTitle(), s.getStatus(), s.getPriority(),
                        s.getStoryPoints(), s.getAcceptanceCriteria().size()))
                .toList();
        long pending = suggestions.findAllByProjectIdAndStatus(projectId, SuggestionStatus.PENDING,
                PageRequest.of(0, 1)).getTotalElements();
        List<BacklogOverview.SessionLine> recentSessions = sessions.findAllByProjectId(projectId,
                        PageRequest.of(0, OVERVIEW_SESSIONS, Sort.by(Sort.Direction.DESC, "startedAt")))
                .stream()
                .map(s -> new BacklogOverview.SessionLine(s.getTitle(), s.getStartedAt(), s.getStatus()))
                .toList();
        return new BacklogOverview(total, byStatus, newest, pending, recentSessions);
    }
}
