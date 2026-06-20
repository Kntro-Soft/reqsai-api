package com.kntro.reqsai.iam.domain.model;

/** Lifecycle of an {@link Account}. */
public enum AccountStatus {

    /** Created but the email has not been verified yet — cannot authenticate. */
    PENDING_VERIFICATION,

    /** Active and able to authenticate. */
    ACTIVE,

    /** Suspended administratively — cannot authenticate. */
    SUSPENDED,

    /** Logically deleted — cannot be recovered through standard flows. */
    DELETED
}
