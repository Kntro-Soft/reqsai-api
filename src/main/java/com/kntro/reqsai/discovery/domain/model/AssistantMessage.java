package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One message of a project's assistant chat, the text conversation the analyst holds with ReqsAI from
 * the capture page, with or without a live session. An analyst message is what they typed; an
 * assistant message is ReqsAI's reply, plus the suggestions it raised for review when the analyst asked
 * for a requirement ({@link #getSuggestionIds()}).
 */
@Entity
@Table(name = "assistant_messages")
@Getter
public class AssistantMessage extends AggregateRoot {

    /** Longest message an analyst may type. */
    public static final int ANALYST_MAX = 2000;
    /** Safety cap on a stored reply; the model is asked for a few sentences. */
    private static final int ASSISTANT_MAX = 8000;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16, updatable = false)
    private AssistantMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "suggestion_ids", columnDefinition = "uuid[]", nullable = false)
    private List<UUID> suggestionIds = new ArrayList<>();

    protected AssistantMessage() {
        super();
    }

    private AssistantMessage(UUID projectId, AssistantMessageRole role, String content, List<UUID> suggestionIds) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.role = role;
        this.content = content;
        this.suggestionIds = new ArrayList<>(suggestionIds);
    }

    /** What the analyst typed; blank text is rejected and the length is capped at {@link #ANALYST_MAX}. */
    public static AssistantMessage fromAnalyst(UUID projectId, String content) {
        String text = Assert.maxLength(Assert.notBlank(content, "content"), "content", ANALYST_MAX).strip();
        return new AssistantMessage(projectId, AssistantMessageRole.ANALYST, text, List.of());
    }

    /** ReqsAI's reply, linked to the suggestions it raised for review (possibly none). */
    public static AssistantMessage fromAssistant(UUID projectId, String content, List<UUID> suggestionIds) {
        String text = Assert.notBlank(content, "content").strip();
        if (text.length() > ASSISTANT_MAX) {
            text = text.substring(0, ASSISTANT_MAX);
        }
        return new AssistantMessage(projectId, AssistantMessageRole.ASSISTANT, text,
                Assert.notNull(suggestionIds, "suggestionIds"));
    }

    public List<UUID> getSuggestionIds() {
        return Collections.unmodifiableList(suggestionIds);
    }
}
