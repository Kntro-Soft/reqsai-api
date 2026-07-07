package com.kntro.reqsai.integrations.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Request body to set a project's Jira push target")
public record SaveProjectTargetRequest(
        @Schema(description = "Organization integration connection id")
        @NotNull
        UUID connectionId,

        @Schema(description = "Jira project key", example = "PAY", maxLength = 100)
        @NotBlank @Size(max = 100)
        String jiraProjectKey,

        @Schema(description = "Jira issue type name", example = "Story", maxLength = 100)
        @NotBlank @Size(max = 100)
        String issueTypeName
) {}
