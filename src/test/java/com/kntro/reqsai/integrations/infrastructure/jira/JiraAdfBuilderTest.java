package com.kntro.reqsai.integrations.infrastructure.jira;

import com.kntro.reqsai.discovery.api.AcceptanceCriterionView;
import com.kntro.reqsai.discovery.api.StoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Infrastructure: Jira ADF builder")
class JiraAdfBuilderTest {

    @Test
    @DisplayName("builds a valid ADF doc with the story statement, metadata and criteria bullets")
    void builds_adf() {
        StoryView story = new StoryView(
                UUID.randomUUID(), UUID.randomUUID(),
                "Login with Google", "user", "sign in with my Google account", "I don't manage another password",
                "HIGH", 3,
                List.of(new AcceptanceCriterionView("Happy path", "I have a Google account", "I click sign in", "I am logged in")));

        Map<String, Object> doc = JiraAdfBuilder.buildDescription(story);

        assertThat(doc).containsEntry("type", "doc").containsEntry("version", 1);
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) doc.get("content");
        // paragraph (story) + paragraph (meta) + heading + bulletList
        assertThat(content).hasSize(4);
        String json = doc.toString();
        assertThat(json).contains("As user, I want to sign in with my Google account, so that I don't manage another password.");
        assertThat(json).contains("Priority: HIGH");
        assertThat(json).contains("Story points: 3");
        assertThat(json).contains("Acceptance Criteria");
        assertThat(json).contains("Given I have a Google account, When I click sign in, Then I am logged in.");
    }

    @Test
    @DisplayName("omits the acceptance-criteria section when there are none")
    void omits_criteria_when_empty() {
        StoryView story = new StoryView(
                UUID.randomUUID(), UUID.randomUUID(),
                "Title", "user", "do", "benefit", "LOW", null, List.of());

        Map<String, Object> doc = JiraAdfBuilder.buildDescription(story);

        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) doc.get("content");
        // just the two paragraphs, no heading/list
        assertThat(content).hasSize(2);
        assertThat(doc.toString()).doesNotContain("Acceptance Criteria");
        assertThat(doc.toString()).doesNotContain("Story points");
    }
}
