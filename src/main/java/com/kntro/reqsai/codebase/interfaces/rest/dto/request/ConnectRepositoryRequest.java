package com.kntro.reqsai.codebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "A GitHub repository to connect to the project")
public record ConnectRepositoryRequest(

        @Schema(description = "owner/name or a github.com URL", example = "acme/reservas",
                maxLength = 300, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 300)
        String repository,

        @Schema(description = "Branch to read; the repository's default branch when omitted", example = "main",
                maxLength = 255, nullable = true)
        @Size(max = 255)
        @Nullable String branch,

        @Schema(description = "The organization's GitHub App installation that shares the repository (from the"
                + " repository picker). When omitted, the installation on the repository's account is used if"
                + " there is one; otherwise only a public repository can be read.", nullable = true, example = "1001")
        @Positive
        @Nullable Long installationId
) {
}
