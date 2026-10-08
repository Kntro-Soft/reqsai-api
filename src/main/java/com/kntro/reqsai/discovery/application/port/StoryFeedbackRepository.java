package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.StoryFeedback;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence of what clients said about stories through share links (tenant schema). */
public interface StoryFeedbackRepository {

    StoryFeedback save(StoryFeedback feedback);

    /** Feedback on the given stories, oldest first. */
    List<StoryFeedback> findAllByStoryIds(Collection<UUID> storyIds);

    long countByShareLink(UUID shareLinkId);
}
