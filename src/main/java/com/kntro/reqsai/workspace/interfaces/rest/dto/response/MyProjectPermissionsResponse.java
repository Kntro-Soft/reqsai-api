package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import com.kntro.reqsai.workspace.domain.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "The caller's effective permissions on a project")
public record MyProjectPermissionsResponse(

        @Schema(description = "The permissions the caller effectively holds on the project",
                example = "[\"STORY_READ\", \"DOCUMENT_READ\"]")
        Set<Permission> permissions
) {}
