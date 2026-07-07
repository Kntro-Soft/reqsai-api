package com.kntro.reqsai.gateway.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A selectable Jira project. */
@Schema(description = "A Jira project visible to the connection")
public record JiraProjectResponse(String key, String name) {}
