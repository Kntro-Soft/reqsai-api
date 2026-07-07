package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.OrgRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Request body to create an organization member")
public record CreateMemberRequest(
        @Schema(description = "Known user id for direct active membership; omit for pending invitation", nullable = true)
        @Nullable UUID userId,

        @Schema(description = "Member email address", example = "user@example.com")
        @NotBlank @Email String email,

        @Schema(description = "Display name of the member or invitee", example = "Jane Doe", maxLength = 150)
        @NotBlank @Size(max = 150) String displayName,

        @Schema(description = "Organization role", allowableValues = {"ADMIN", "MEMBER"}, example = "MEMBER")
        @NotNull OrgRole role
) {}
