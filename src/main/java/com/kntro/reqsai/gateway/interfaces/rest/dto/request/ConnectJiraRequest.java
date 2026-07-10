package com.kntro.reqsai.gateway.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to connect a Jira Cloud integration at the organization level")
public record ConnectJiraRequest(
        @Schema(description = "Jira site base URL", example = "https://acme.atlassian.net", maxLength = 500)
        @NotBlank @Size(max = 500)
        String siteUrl,

        @Schema(description = "Jira account email", example = "pm@acme.com", maxLength = 320)
        @NotBlank @Email @Size(max = 320)
        String email,

        @Schema(description = "Jira API token (stored encrypted, never returned)")
        @NotBlank
        String apiToken
) {}
