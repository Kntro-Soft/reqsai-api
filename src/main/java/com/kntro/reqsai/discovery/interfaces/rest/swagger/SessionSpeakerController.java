package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.UpdateSessionSpeakerRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SessionSpeakerResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SessionSpeakersResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for the diarized speakers of a session (US40), implemented by
 * {@code controllers.SessionSpeakerControllerImpl}. The analyst names each speaker and says whether they are
 * the client or the team, so the AI prioritizes the client's needs. Tenant-scoped through the JWT
 * {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/sessions/{sessionId}/speakers",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Discovery Sessions", description = "Requirements-elicitation sessions — lifecycle from DRAFT to COMPLETED")
public interface SessionSpeakerController {

    @Operation(
            summary = "List the speakers of a session",
            description = """
                    The speakers the speech-to-text provider told apart (diarization), numbered by first \
                    appearance in the transcript, with the name and side (CLIENT / TEAM) the analyst gave. \
                    A speaker the analyst has not named is called "Hablante N". Also reports the stretches \
                    where two speakers talked at the same time for at least 500 ms, where the speaker \
                    attribution may be wrong. Empty lists when the transcript has no speaker labels. \
                    Requires SESSION_READ.""")
    @ApiResponse(responseCode = "200", description = "Speakers and overlapping speech",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SessionSpeakersResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "sessionId": "019756a0-1234-7abc-8def-000000000001",
                              "speakers": [
                                {"label": "0", "index": 1, "displayName": "Ana Torres", "name": "Ana Torres",
                                 "side": "CLIENT", "segmentCount": 14},
                                {"label": "1", "index": 2, "displayName": null, "name": "Hablante 2",
                                 "side": null, "segmentCount": 9}
                              ],
                              "overlaps": {
                                "count": 1,
                                "totalMs": 1700,
                                "ranges": [{"startMs": 61200, "endMs": 62900, "speakerLabels": ["0", "1"]}]
                              }
                            }""")))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<SessionSpeakersResponse> list(
            @Parameter(description = "Project the session belongs to", required = true) @PathVariable UUID projectId,
            @Parameter(description = "The session", required = true) @PathVariable UUID sessionId);

    @Operation(
            summary = "Name a speaker and set their side",
            description = """
                    Gives a diarized speaker a real name and says whether they are the CLIENT (their needs are \
                    the requirements) or the TEAM (context, not requirements unless the client agrees). The \
                    description applies to every segment of that speaker, past and future, to the speaker \
                    tags the AI reads from then on, and is pushed live to the session's viewers \
                    (SPEAKER_UPDATED). A null or blank name goes back to "Hablante N"; a null side unsets it. \
                    Answers 404 SPEAKER_NOT_FOUND when that label never spoke in the session. Requires \
                    SESSION_RUN.""")
    @ApiResponse(responseCode = "200", description = "The speaker as now described",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SessionSpeakerResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/{label}", consumes = MediaType.APPLICATION_JSON_VALUE, version = ApiVersioning.V1)
    ResponseEntity<SessionSpeakerResponse> update(
            @Parameter(description = "Project the session belongs to", required = true) @PathVariable UUID projectId,
            @Parameter(description = "The session", required = true) @PathVariable UUID sessionId,
            @Parameter(description = "Diarization label of the speaker", required = true, example = "0")
            @PathVariable String label,
            @Valid @RequestBody UpdateSessionSpeakerRequest request);
}
