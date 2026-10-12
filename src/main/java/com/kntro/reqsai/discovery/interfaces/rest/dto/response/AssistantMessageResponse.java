package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "One message of a project's assistant chat")
public record AssistantMessageResponse(

        @Schema(description = "Message identifier")
        UUID id,

        @Schema(description = "Who wrote it", allowableValues = {"ANALYST", "ASSISTANT"})
        String role,

        @Schema(description = "Message text (plain text)")
        String content,

        @Schema(description = "When it was written")
        Instant createdAt,

        @Schema(description = "Suggestions the reply raised for review, in their current state (empty for analyst messages)")
        List<SuggestionResponse> suggestions,

        @Schema(description = "Whether the reply came from searching the pure source code of the connected repositories")
        boolean codeSearched,

        @Schema(description = "Places in the code the reply cites after a deep search: file, lines and GitHub link")
        List<CodeReferenceResponse> codeReferences
) {

    public AssistantMessageResponse(UUID id, String role, String content, Instant createdAt,
                                    List<SuggestionResponse> suggestions) {
        this(id, role, content, createdAt, suggestions, false, List.of());
    }
}
