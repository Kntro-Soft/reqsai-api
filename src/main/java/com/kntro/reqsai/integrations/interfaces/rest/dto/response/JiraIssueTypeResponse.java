package com.kntro.reqsai.integrations.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A selectable Jira issue type. */
@Schema(description = "A Jira issue type available for a project")
public record JiraIssueTypeResponse(String id, String name) {}
