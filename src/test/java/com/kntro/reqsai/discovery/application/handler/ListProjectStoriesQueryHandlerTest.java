package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.ListProjectStoriesQuery;
import com.kntro.reqsai.discovery.application.query.StoryFilter;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.PaginationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Project Stories")
@ExtendWith(MockitoExtension.class)
class ListProjectStoriesQueryHandlerTest {

    @Mock
    private UserStoryRepository stories;

    private final PageRequestFactory pageRequestFactory = new PageRequestFactory(new PaginationProperties(20, 100));

    private ListProjectStoriesQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListProjectStoriesQueryHandler(stories, pageRequestFactory);
    }

    @Test
    @DisplayName("should forward the filter and a validated pageable to the repository")
    void should_forward_filter_and_pageable() {
        UUID projectId = UUID.randomUUID();
        UserStory story = UserStoryMother.draft().withProjectId(projectId).build();
        Page<UserStory> page = new PageImpl<>(List.of(story), PageRequest.of(0, 20), 1);

        StoryFilter filter = new StoryFilter(
                "upload", StoryStatus.DRAFT, Priority.HIGH,
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(stories.findAllByProjectId(eq(projectId), eq(filter), pageable.capture())).thenReturn(page);

        Page<UserStory> result = handler.handle(new ListProjectStoriesQuery(
                projectId, PageCriteria.of(0, 20, "title", "ASC"), filter));

        assertThat(result.getContent()).containsExactly(story);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("title")).isNotNull();
    }

    @Test
    @DisplayName("should pass the empty filter through unchanged (original listing behavior)")
    void should_pass_empty_filter() {
        UUID projectId = UUID.randomUUID();
        when(stories.findAllByProjectId(eq(projectId), eq(StoryFilter.none()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<UserStory> result = handler.handle(new ListProjectStoriesQuery(
                projectId, PageCriteria.of(null, null, null, null), StoryFilter.none()));

        assertThat(result.getContent()).isEmpty();
    }
}
