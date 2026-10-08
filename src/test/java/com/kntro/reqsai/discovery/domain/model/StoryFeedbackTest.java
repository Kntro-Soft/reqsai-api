package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StoryFeedback")
class StoryFeedbackTest {

    private static final UUID STORY = UUID.randomUUID();
    private static final UUID LINK = UUID.randomUUID();

    @Test
    @DisplayName("an approval may come without a note; a blank note is dropped")
    void approval() {
        StoryFeedback feedback = StoryFeedback.approval(STORY, LINK, "  María Quispe ", "  ");

        assertThat(feedback.getKind()).isEqualTo(StoryFeedbackKind.APPROVAL);
        assertThat(feedback.getAuthorName()).isEqualTo("María Quispe");
        assertThat(feedback.getComment()).isNull();
    }

    @Test
    @DisplayName("a comment needs text and a signer")
    void comment() {
        assertThat(StoryFeedback.comment(STORY, LINK, "Ana", " 24 horas, no 2 ").getComment())
                .isEqualTo("24 horas, no 2");
        assertThatThrownBy(() -> StoryFeedback.comment(STORY, LINK, "Ana", " "))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StoryFeedback.comment(STORY, LINK, " ", "texto"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("refuses comments and names over their limits")
    void limits() {
        assertThatThrownBy(() -> StoryFeedback.comment(STORY, LINK, "Ana", "x".repeat(StoryFeedback.COMMENT_MAX + 1)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StoryFeedback.approval(STORY, LINK, "x".repeat(StoryFeedback.AUTHOR_MAX + 1), null))
                .isInstanceOf(DomainException.class);
    }
}
