package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A client's approval of, or comment on, a story, left through a share link (US50). It is the
 * client's voice for the team: it does not change the story's review status, which stays the team's
 * decision ({@code STORY_APPROVE}).
 */
@Entity
@Table(name = "story_feedback")
@Getter
public class StoryFeedback extends AggregateRoot {

    public static final int AUTHOR_MAX = 120;
    public static final int COMMENT_MAX = 2000;

    @Column(name = "story_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID storyId;

    @Column(name = "share_link_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID shareLinkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private StoryFeedbackKind kind;

    @Column(name = "author_name", nullable = false, length = AUTHOR_MAX, updatable = false)
    private String authorName;

    @Column(name = "comment", columnDefinition = "text", updatable = false)
    private @Nullable String comment;

    protected StoryFeedback() {
        super();
    }

    private StoryFeedback(UUID storyId, UUID shareLinkId, StoryFeedbackKind kind, String authorName,
                          @Nullable String comment) {
        super();
        this.storyId = Assert.notNull(storyId, "storyId");
        this.shareLinkId = Assert.notNull(shareLinkId, "shareLinkId");
        this.kind = Assert.notNull(kind, "kind");
        this.authorName = Assert.maxLength(Assert.notBlank(authorName, "authorName"), "authorName", AUTHOR_MAX).strip();
        this.comment = comment;
    }

    /** The client approves the story; an optional note may come with it. */
    public static StoryFeedback approval(UUID storyId, UUID shareLinkId, String authorName, @Nullable String note) {
        String text = note == null || note.isBlank() ? null
                : Assert.maxLength(note, "comment", COMMENT_MAX).strip();
        return new StoryFeedback(storyId, shareLinkId, StoryFeedbackKind.APPROVAL, authorName, text);
    }

    /** The client comments on the story; the comment is required. */
    public static StoryFeedback comment(UUID storyId, UUID shareLinkId, String authorName, String comment) {
        String text = Assert.maxLength(Assert.notBlank(comment, "comment"), "comment", COMMENT_MAX).strip();
        return new StoryFeedback(storyId, shareLinkId, StoryFeedbackKind.COMMENT, authorName, text);
    }
}
