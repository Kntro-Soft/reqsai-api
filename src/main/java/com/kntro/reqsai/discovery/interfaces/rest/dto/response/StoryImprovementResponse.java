package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Schema(description = "What the AI proposes to improve a story. Nothing is stored: the story is unchanged until the "
        + "proposal is applied")
public record StoryImprovementResponse(

        @Schema(description = "Story the proposal is for", example = "019756a0-1234-7abc-8def-000000000010")
        UUID storyId,

        @Schema(description = "The improved story. A value the source does not state is a [[...]] token")
        Proposal proposal,

        @Schema(description = "The [[...]] tokens of the proposal, each with the question that tells what to supply. "
                + "Every token appears verbatim in the proposal text and must be replaced before applying it")
        List<PendingValue> pendingValues,

        @Schema(description = "One short sentence per change the AI made, in the language of the story")
        List<String> fixes,

        @Schema(description = "The quality the proposal would have (rule-based evaluation of the proposal as it is, "
                + "tokens included). Not stored")
        StoryQualityResponse expectedQuality
) {

    @Schema(description = "The text of the improved story")
    public record Proposal(
            @Schema(example = "Exportar el backlog aprobado a CSV") String title,
            @Schema(example = "analista de cumplimiento") String role,
            @Schema(example = "exportar el backlog aprobado a un archivo CSV") String action,
            @Schema(example = "compartirlo con el cliente sin reescribirlo") String benefit,
            @Schema(description = "Acceptance criteria in Given / When / Then format") List<Criterion> acceptanceCriteria
    ) {
    }

    @Schema(description = "One Given / When / Then acceptance criterion")
    public record Criterion(
            @Schema(description = "Optional scenario label", nullable = true) @Nullable String scenario,
            @Schema(example = "hay historias aprobadas en el backlog") String given,
            @Schema(example = "el analista exporta el backlog") String when,
            @Schema(example = "el sistema responde en menos de [[tiempo máximo de respuesta]] segundos") String then
    ) {
    }

    @Schema(description = "A value the analyst must supply")
    public record PendingValue(
            @Schema(description = "The literal placeholder, as it appears in the proposal",
                    example = "[[tiempo máximo de respuesta]]") String token,
            @Schema(description = "A question for the analyst that says what to supply",
                    example = "¿En cuántos segundos debe responder?") String hint
    ) {
    }
}
