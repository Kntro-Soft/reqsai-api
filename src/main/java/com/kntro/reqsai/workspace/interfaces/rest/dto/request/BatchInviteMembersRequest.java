package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.OrgRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body to invite several organization members in one atomic operation")
public record BatchInviteMembersRequest(
        @Schema(description = "Invitations to create; all-or-nothing")
        @NotEmpty @Valid List<Invitation> invitations
) {
    @Schema(description = "A single pending invitation")
    public record Invitation(
            @Schema(description = "Invitee email address", example = "user@example.com")
            @NotBlank @Email String email,

            @Schema(description = "Display name of the invitee", example = "Jane Doe", maxLength = 150)
            @NotBlank @Size(max = 150) String displayName,

            @Schema(description = "Organization role", allowableValues = {"ADMIN", "MEMBER"}, example = "MEMBER")
            @NotNull OrgRole role
    ) {}
}
