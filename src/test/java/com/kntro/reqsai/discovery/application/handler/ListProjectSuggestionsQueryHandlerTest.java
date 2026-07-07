package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.query.ListProjectSuggestionsQuery;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.PaginationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Project Suggestions")
@ExtendWith(MockitoExtension.class)
class ListProjectSuggestionsQueryHandlerTest {

    @Mock
    private SuggestionRepository suggestions;

    private final PageRequestFactory pageRequestFactory = new PageRequestFactory(new PaginationProperties(20, 100));

    private ListProjectSuggestionsQueryHandler handler() {
        return new ListProjectSuggestionsQueryHandler(suggestions, pageRequestFactory);
    }

    @Test
    @DisplayName("should default to PENDING when no status is given")
    void should_default_to_pending() {
        UUID projectId = UUID.randomUUID();
        Suggestion s = Suggestion.newStory(UUID.randomUUID(), projectId,
                "Login", "user", "log in", "access", Priority.HIGH, 3);
        Page<Suggestion> page = new PageImpl<>(List.of(s), PageRequest.of(0, 20), 1);
        when(suggestions.findAllByProjectIdAndStatus(eq(projectId), eq(SuggestionStatus.PENDING), any()))
                .thenReturn(page);

        Page<Suggestion> result = handler().handle(
                new ListProjectSuggestionsQuery(projectId, null, PageCriteria.of(0, 20, null, null)));

        assertThat(result.getContent()).containsExactly(s);
    }

    @Test
    @DisplayName("should pass through an explicit status filter")
    void should_pass_explicit_status() {
        UUID projectId = UUID.randomUUID();
        Page<Suggestion> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(suggestions.findAllByProjectIdAndStatus(eq(projectId), eq(SuggestionStatus.ACCEPTED), any()))
                .thenReturn(empty);

        Page<Suggestion> result = handler().handle(
                new ListProjectSuggestionsQuery(projectId, SuggestionStatus.ACCEPTED, PageCriteria.of(0, 20, null, null)));

        assertThat(result.getContent()).isEmpty();
    }
}
