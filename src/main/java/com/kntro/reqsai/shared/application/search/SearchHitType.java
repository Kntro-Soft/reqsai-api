package com.kntro.reqsai.shared.application.search;

/**
 * Kind of entity a {@link SearchHit} points to. Shared across bounded contexts so each context's
 * {@code search} named interface can tag its own results, and the {@code search} aggregator module
 * can merge them without a cross-context type dependency.
 */
public enum SearchHitType {
    PROJECT,
    USER_STORY,
    ORGANIZATION,
    MEMBER,
    GLOSSARY_TERM,
    DOCUMENT
}
