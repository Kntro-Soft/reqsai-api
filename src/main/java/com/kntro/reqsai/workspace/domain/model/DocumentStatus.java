package com.kntro.reqsai.workspace.domain.model;

/**
 * Lifecycle of a {@link ProjectDocument}. An uploaded client document is {@link #PENDING} while the
 * analyst reviews what the AI extracted from it; applying the review makes it {@link #ACTIVE}. Pending
 * documents are invisible to listings, search and the AI context.
 */
public enum DocumentStatus {
    PENDING,
    ACTIVE,
    ARCHIVED
}
