package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request body to invite several NEW people (by email) directly to a project, all-or-nothing")
public record InviteProjectMembersRequest(
        @Schema(description = "Invitations to create; all-or-nothing")
        @NotEmpty @Valid List<Invitation> invitations
) {
    @Schema(description = "A single project-scoped invitation")
    public record Invitation(
            @Schema(description = "Invitee email address", example = "user@example.com")
            @NotBlank @Email String email,

            @Schema(description = "Display name of the invitee", example = "Jane Doe", maxLength = 150)
            @NotBlank @Size(max = 150) String displayName,

            @Schema(description = "Project role id to assign the invitee on accept")
            @NotNull UUID roleId
    ) {}
}
