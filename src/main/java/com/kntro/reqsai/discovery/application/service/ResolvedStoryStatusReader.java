package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fills each accepted suggestion with the current review status of the story it resolved to, so the
 * feeds can say whether the decision ended in an approved story or a draft. One query per call.
 */
@Component
@RequiredArgsConstructor
public class ResolvedStoryStatusReader {

    private final UserStoryRepository stories;

    public void attach(UUID projectId, Collection<Suggestion> suggestions) {
        List<UUID> ids = suggestions.stream()
                .map(Suggestion::getResolvedStoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<UUID, StoryStatus> byId = stories.findAllByProjectIdAndIdIn(projectId, ids).stream()
                .collect(Collectors.toMap(UserStory::getId, UserStory::getStatus, (a, b) -> a));
        suggestions.forEach(s -> {
            if (s.getResolvedStoryId() != null) {
                s.attachResolvedStoryStatus(byId.get(s.getResolvedStoryId()));
            }
        });
    }
}
