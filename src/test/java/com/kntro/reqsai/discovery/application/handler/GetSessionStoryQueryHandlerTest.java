package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetSessionStoryQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.mothers.UserStoryMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("Application: Get Session Story")
@ExtendWith(MockitoExtension.class)
class GetSessionStoryQueryHandlerTest {

    @Mock
    private UserStoryRepository stories;
    @InjectMocks
    private GetSessionStoryQueryHandler handler;

    @Test
    @DisplayName("should throw 404 when story does not exist")
    void should_throw_not_found_when_missing() {
        UUID sessionId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(stories.findById(storyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetSessionStoryQuery(sessionId, storyId)))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
    }

    @Test
    @DisplayName("should throw 404 when story was created manually (null sessionId)")
    void should_throw_not_found_for_manual_story() {
        UUID sessionId = UUID.randomUUID();
        // UserStoryMother builds manual stories (sessionId = null)
        UserStory manual = UserStoryMother.draft().build();
        when(stories.findById(manual.getId())).thenReturn(Optional.of(manual));

        assertThatThrownBy(() -> handler.handle(new GetSessionStoryQuery(sessionId, manual.getId())))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(ex -> assertThat(((EntityNotFoundException) ex).error())
                        .isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
    }
}
