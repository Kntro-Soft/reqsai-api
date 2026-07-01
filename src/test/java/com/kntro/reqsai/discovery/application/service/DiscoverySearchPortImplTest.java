package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoveryLexicalSearchRepository;
import com.kntro.reqsai.discovery.search.DiscoverySearchPort;
import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.shared.application.search.SearchHitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: Discovery Search Port")
@ExtendWith(MockitoExtension.class)
class DiscoverySearchPortImplTest {

    @Mock private DiscoveryLexicalSearchRepository lexicalSearch;

    private DiscoverySearchPort port() {
        return new DiscoverySearchPortImpl(lexicalSearch);
    }

    private SearchHit story() {
        return new SearchHit(SearchHitType.USER_STORY, UUID.randomUUID(), "Story", null, UUID.randomUUID());
    }

    @Test
    @DisplayName("unrestricted scope searches every story in the tenant")
    void unrestricted_searches_all() {
        SearchHit hit = story();
        when(lexicalSearch.searchAllUserStories("upload", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchUserStories("upload", 8, ProjectScope.unrestricted());

        assertThat(result).containsExactly(hit);
        verify(lexicalSearch, never()).searchUserStoriesInProjects(any(), any(), anyInt());
    }

    @Test
    @DisplayName("restricted scope searches only the accessible projects")
    void restricted_searches_scoped_projects() {
        UUID projectId = UUID.randomUUID();
        SearchHit hit = story();
        when(lexicalSearch.searchUserStoriesInProjects(Set.of(projectId), "upload", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchUserStories("upload", 8, ProjectScope.restrictedTo(Set.of(projectId)));

        assertThat(result).containsExactly(hit);
        verify(lexicalSearch, never()).searchAllUserStories(any(), anyInt());
    }

    @Test
    @DisplayName("empty scope short circuits without querying")
    void empty_scope_returns_empty() {
        List<SearchHit> result = port().searchUserStories("upload", 8, ProjectScope.restrictedTo(Set.of()));

        assertThat(result).isEmpty();
        verifyNoInteractions(lexicalSearch);
    }
}
