package com.kntro.reqsai.discovery.insights;

import java.util.UUID;

/**
 * A story with a review decision in a period, together with the text the AI proposed and the text it says now.
 *
 * @param storyId
 *            the story
 * @param status
 *            the decision: {@code APPROVED}, {@code EXPORTED} (accepted) or {@code REJECTED}
 * @param generatedText
 *            what the AI proposed, frozen when the story was generated or accepted from a suggestion
 * @param currentText
 *            what the story says now, composed the same way
 */
public record DecidedStory(UUID storyId, String status, String generatedText, String currentText) {
}
