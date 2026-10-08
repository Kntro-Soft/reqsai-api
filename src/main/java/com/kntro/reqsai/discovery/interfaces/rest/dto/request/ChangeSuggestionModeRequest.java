package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.SuggestionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "When the assistant analyzes the session's conversation")
public record ChangeSuggestionModeRequest(

        @Schema(description = "AUTO: on its own as the meeting goes; MANUAL: only when the analyst asks (Analizar ahora)",
                example = "MANUAL", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AUTO", "MANUAL"})
        @NotNull
        SuggestionMode mode
) {
}
