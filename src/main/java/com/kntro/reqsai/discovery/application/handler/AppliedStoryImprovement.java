package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;

/** A story after an improvement was applied, with the quality evaluated and stored on it. */
public record AppliedStoryImprovement(UserStory story, StoryQualityAssessment quality) {
}
