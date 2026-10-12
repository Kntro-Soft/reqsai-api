package com.kntro.reqsai.discovery.domain.model;

/** What a share link lets its visitor do: only follow along, or also decide. */
public enum ShareAccess {
    /** Read only: follows the session as a spectator. */
    VIEW,
    /** Reads, and also accepts or dismisses suggestions and approves or rejects stories. */
    REVIEW
}
