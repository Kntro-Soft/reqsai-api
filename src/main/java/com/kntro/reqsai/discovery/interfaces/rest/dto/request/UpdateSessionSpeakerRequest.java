package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.SpeakerSide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "How the analyst describes one diarized speaker of a session")
public record UpdateSessionSpeakerRequest(

        @Schema(description = "Real name of the speaker; null or blank goes back to the default \"Hablante N\"",
                example = "Ana Torres", maxLength = 80, nullable = true)
        @Size(max = 80)
        @Nullable String displayName,

        @Schema(description = "Side of the meeting: CLIENT (their needs are the requirements) or TEAM (context); "
                + "null leaves it unset", example = "CLIENT", allowableValues = {"CLIENT", "TEAM"}, nullable = true)
        @Nullable SpeakerSide side
) {
}
