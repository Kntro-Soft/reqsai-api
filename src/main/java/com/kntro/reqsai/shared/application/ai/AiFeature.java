package com.kntro.reqsai.shared.application.ai;

/**
 * The product feature an AI call is made for. Every row of the usage ledger carries one, so the usage
 * page can say where an organization's tokens went in terms users recognise.
 */
public enum AiFeature {

    /** Suggestions raised while a meeting is being captured. */
    LIVE_SUGGESTIONS,

    /** The assistant chat of the capture page, including the requirement it turns into a suggestion. */
    CHAT,

    /** The pass that flushes the rest of a meeting when it is stopped, and whole-transcript extraction. */
    FINAL_STORIES,

    /** Describing the modules of a connected repository. */
    CODE_INDEX,

    /** Proposing a new project's name, description and technical profile from its Jira and GitHub sources. */
    PROJECT_SETUP,
    /** Questions asked directly to the code of a connected repository. */
    CODE_DEEP_SEARCH,

    /** Reading an uploaded client document into a summary, glossary terms and constraints. */
    DOCUMENT_CLASSIFICATION,

    /** Embedding vectors for similarity search; billable but cheap, so weighted down in the quota. */
    EMBEDDINGS,

    /** Speech-to-text, metered in seconds and never charged against the token quota. */
    TRANSCRIPTION,

    /** A call made outside any {@link AiUsageScope}. */
    OTHER
}
