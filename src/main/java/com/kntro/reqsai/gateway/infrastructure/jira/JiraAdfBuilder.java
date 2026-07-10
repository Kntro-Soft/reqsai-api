package com.kntro.reqsai.gateway.infrastructure.jira;

import com.kntro.reqsai.discovery.api.AcceptanceCriterionView;
import com.kntro.reqsai.discovery.api.StoryView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a Jira issue description as an <strong>Atlassian Document Format (ADF)</strong> document
 * (Jira Cloud REST v3 requires ADF, not wiki markup) from a Reqs-AI {@link StoryView}.
 * <p>
 * Layout: a "As {role}, I want to {action}, so that {benefit}." paragraph, the priority/story-point
 * metadata, then an "Acceptance Criteria" heading with one bullet per criterion rendered as
 * {@code Given … When … Then …}. The returned map is the {@code description} field value.
 */
public final class JiraAdfBuilder {

    private JiraAdfBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Builds the ADF {@code doc} node for the given story. */
    public static Map<String, Object> buildDescription(StoryView story) {
        List<Object> content = new ArrayList<>();

        content.add(paragraph("As %s, I want to %s, so that %s.".formatted(
                story.role(), story.action(), story.benefit())));

        String meta = "Priority: " + story.priority()
                + (story.storyPoints() != null ? "  •  Story points: " + story.storyPoints() : "");
        content.add(paragraph(meta));

        List<AcceptanceCriterionView> criteria = story.acceptanceCriteria();
        if (criteria != null && !criteria.isEmpty()) {
            content.add(heading("Acceptance Criteria"));
            content.add(bulletList(criteria));
        }

        return Map.of("type", "doc", "version", 1, "content", content);
    }

    private static Map<String, Object> paragraph(String text) {
        return Map.of("type", "paragraph", "content", List.of(textNode(text)));
    }

    private static Map<String, Object> heading(String text) {
        return Map.of("type", "heading", "attrs", Map.of("level", 3),
                "content", List.of(textNode(text)));
    }

    private static Map<String, Object> bulletList(List<AcceptanceCriterionView> criteria) {
        List<Object> items = new ArrayList<>();
        for (AcceptanceCriterionView c : criteria) {
            StringBuilder line = new StringBuilder();
            if (c.scenario() != null && !c.scenario().isBlank()) {
                line.append(c.scenario()).append(": ");
            }
            line.append("Given ").append(c.given())
                    .append(", When ").append(c.when())
                    .append(", Then ").append(c.then()).append('.');
            items.add(Map.of("type", "listItem", "content", List.of(paragraph(line.toString()))));
        }
        return Map.of("type", "bulletList", "content", items);
    }

    private static Map<String, Object> textNode(String text) {
        return Map.of("type", "text", "text", text);
    }
}
