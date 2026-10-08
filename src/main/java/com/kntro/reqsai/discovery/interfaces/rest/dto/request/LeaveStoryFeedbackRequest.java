package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.StoryFeedbackKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "A client's approval of, or comment on, a shared story")
public record LeaveStoryFeedbackRequest(

        @Schema(description = "APPROVAL or COMMENT", example = "COMMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        StoryFeedbackKind kind,

        @Schema(description = "Name the client signs with", example = "María Quispe", maxLength = 120,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 120)
        String authorName,

        @Schema(description = "The comment; required for COMMENT, an optional note for APPROVAL",
                example = "El límite de cancelación debería ser 24 horas", maxLength = 2000, nullable = true)
        @Size(max = 2000)
        @Nullable String comment
) {
}
