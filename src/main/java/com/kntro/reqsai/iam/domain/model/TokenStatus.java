package com.kntro.reqsai.iam.domain.model;

/** Lifecycle state of a {@link RefreshToken}. */
public enum TokenStatus {

    /** The token is usable and has not expired. */
    ACTIVE,

    /** The token was explicitly revoked (logout or rotation). */
    REVOKED,

    /** The token passed its {@code expiresAt} deadline (set during cleanup, not by domain logic). */
    EXPIRED
}
