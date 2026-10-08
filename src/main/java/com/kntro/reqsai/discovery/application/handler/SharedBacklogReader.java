package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.StoryFeedbackRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads a shared backlog inside the link's tenant (bound by {@link SharedLinkGateway}). Rejected and
 * merged stories stay hidden: they are the team's discards, not something to put before the client.
 */
@Component
@RequiredArgsConstructor
public class SharedBacklogReader {

    /** Upper bound on the stories one link shows. */
    static final int MAX_STORIES = 300;

    static final Set<StoryStatus> HIDDEN = EnumSet.of(StoryStatus.REJECTED, StoryStatus.MERGED);

    private final UserStoryRepository stories;
    private final StoryFeedbackRepository feedback;
    private final WorkspaceModuleApi workspace;

    @Transactional(readOnly = true)
    public SharedBacklog read(ShareLink link) {
        String projectName = workspace.findProjectSnapshot(link.getProjectId())
                .map(ProjectSnapshot::name)
                .orElseThrow(DiscoveryExceptions::shareLinkUnavailable);
        List<UserStory> shown = stories.findAllByProjectId(link.getProjectId(),
                        PageRequest.of(0, MAX_STORIES, Sort.by(Sort.Direction.ASC, "createdAt")))
                .stream()
                .filter(story -> !HIDDEN.contains(story.getStatus()))
                .toList();
        Map<UUID, List<StoryFeedback>> byStory = feedback.findAllByStoryIds(
                        shown.stream().map(UserStory::getId).toList()).stream()
                .collect(Collectors.groupingBy(StoryFeedback::getStoryId));
        return new SharedBacklog(projectName, link.getExpiresAt(), shown.stream()
                .map(story -> new SharedBacklog.SharedStory(story, byStory.getOrDefault(story.getId(), List.of())))
                .toList());
    }

    /** True when the story belongs to the link's project and is visible through it. */
    @Transactional(readOnly = true)
    public boolean isShared(ShareLink link, UUID storyId) {
        return stories.findByIdAndProjectId(storyId, link.getProjectId())
                .filter(story -> !HIDDEN.contains(story.getStatus()))
                .isPresent();
    }
}
