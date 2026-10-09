package com.kntro.reqsai.discovery.domain.model;

/**
 * What the client's connected code says about a requirement the copilot raised: the capability is already
 * built, or the conversation asks for a rule or behaviour different from the one implemented.
 */
public enum CodeFinding {
    ALREADY_EXISTS,
    CONFLICTS_WITH_CODE
}
