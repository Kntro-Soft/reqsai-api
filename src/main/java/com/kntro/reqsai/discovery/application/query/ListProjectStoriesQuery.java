package com.kntro.reqsai.discovery.application.query;

import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;

import java.util.UUID;

/** Query to list user stories for a project, with optional pagination, sorting and {@link StoryFilter filters}. */
public record ListProjectStoriesQuery(UUID projectId, PageCriteria criteria, StoryFilter filter) {
}
