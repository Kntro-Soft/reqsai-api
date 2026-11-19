package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A verbatim fragment of a meeting a {@link UserStory} comes from (US72): the session it was said in, the
 * transcript segment where it starts and the quote itself. The moment of the meeting is not stored: it is
 * read from the segment, so it follows the transcript.
 *
 * <p>Non-root entity: no repository, always loaded and saved through {@link UserStory}.
 */
@Entity
@Table(name = "story_source_fragments")
@Getter
public class StorySourceFragment extends AuditableEntity {

    public static final int QUOTE_MAX = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false, updatable = false)
    private UserStory story;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID sessionId;

    /** Sequence of the transcript segment where the quote starts; null when it was not located. */
    @Column(name = "sequence", updatable = false)
    private @Nullable Integer sequence;

    @Column(name = "quote", nullable = false, length = QUOTE_MAX, updatable = false)
    private String quote;

    /** Required by JPA, do not use directly. */
    protected StorySourceFragment() {
        super();
    }

    /** Package-private: only {@link UserStory} creates its source fragments. */
    StorySourceFragment(UserStory story, UUID sessionId, @Nullable Integer sequence, String quote) {
        super();
        this.story = Assert.notNull(story, "story");
        this.sessionId = Assert.notNull(sessionId, "sessionId");
        Assert.isTrue(sequence == null || sequence > 0, "sequence", "sequence must be positive");
        this.sequence = sequence;
        this.quote = clip(Assert.notBlank(quote, "quote"));
    }

    /** The quote as stored: stripped and cut to {@link #QUOTE_MAX} characters. */
    private static String clip(String quote) {
        String text = quote.strip();
        return text.length() <= QUOTE_MAX ? text : text.substring(0, QUOTE_MAX - 3) + "...";
    }

    /** True when it is the same quote, of the same session, as the given one. */
    boolean sameAs(UUID otherSessionId, String otherQuote) {
        return sessionId.equals(otherSessionId) && quote.equals(clip(otherQuote));
    }
}
