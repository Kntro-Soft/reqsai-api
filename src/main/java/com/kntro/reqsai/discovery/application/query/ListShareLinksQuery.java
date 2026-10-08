package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/** The share links of a project, newest first. */
public record ListShareLinksQuery(UUID projectId) {
}
