package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.AssistantMessageRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.query.ListAssistantMessagesQuery;
import com.kntro.reqsai.discovery.domain.model.AssistantMessage;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The newest messages of a project's assistant chat, oldest first, each reply with the suggestions it
 * raised in their current review state (accepted, dismissed or still pending).
 */
@Component
@RequiredArgsConstructor
public class ListAssistantMessagesQueryHandler {

    static final int MAX_LIMIT = 100;

    private final AssistantMessageRepository messages;
    private final SuggestionRepository suggestions;

    @Transactional(readOnly = true)
    public List<AssistantChatEntry> handle(ListAssistantMessagesQuery query) {
        int limit = Math.clamp(query.limit(), 1, MAX_LIMIT);
        List<AssistantMessage> chat = messages.findLatestByProjectId(query.projectId(), limit);
        List<UUID> ids = chat.stream().flatMap(m -> m.getSuggestionIds().stream()).toList();
        Map<UUID, Suggestion> byId = suggestions.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Suggestion::getId, Function.identity()));
        return chat.stream()
                .map(m -> new AssistantChatEntry(m, m.getSuggestionIds().stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .toList()))
                .toList();
    }
}
