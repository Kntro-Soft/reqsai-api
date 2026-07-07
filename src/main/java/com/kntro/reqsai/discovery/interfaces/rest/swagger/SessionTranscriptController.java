package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ProcessTranscriptResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.TranscriptResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.TranscriptSegmentPageResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * API contract for session-scoped transcript operations; implemented by
 * {@code controllers.SessionTranscriptControllerImpl}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Discovery Sessions", description = "Requirements-elicitation sessions — lifecycle from DRAFT to COMPLETED")
public interface SessionTranscriptController {

    @Operation(
            summary = "Upload and transcribe a recording (POST /sessions/{id}/upload)",
            description = """
                    Receives a pre-recorded audio file (`multipart/form-data`), transcribes it via Whisper, \
                    and saves the resulting text as the session transcript.

                    The session must be in **DRAFT** status. On success it transitions to **STOPPED** — \
                    ready for AI extraction via `POST /sessions/{id}/process`.

                    To retry the AI extraction without re-uploading the audio, call `POST /sessions/{id}/process` \
                    again directly.""")
    @ApiResponse(
            responseCode = "200",
            description = "Audio transcribed — session is now STOPPED",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000001",
                              "projectId": "019756a0-1234-7abc-8def-000000000002",
                              "title": "Sprint 24 — Requirements Elicitation",
                              "language": "es-PE",
                              "status": "STOPPED",
                              "startedAt": null,
                              "endedAt": "2026-06-15T15:30:00Z",
                              "audioDurationMs": 0,
                              "processingError": null,
                              "createdAt": "2026-06-15T13:55:00Z",
                              "updatedAt": "2026-06-15T15:30:05Z"
                            }""")))
    @ApiResponse(responseCode = "422", description = "Session is not in DRAFT status")
    @ApiResponse(responseCode = "503", description = "Transcription service not configured or unavailable")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/upload", version = ApiVersioning.V1, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DiscoverySessionResponse> upload(
            @Parameter(description = "Session to upload the recording to", required = true)
            @PathVariable UUID sessionId,
            @Parameter(description = "Audio file (MP3, WAV, M4A)", required = true)
            @RequestParam("file") MultipartFile file);

    @Operation(
            summary = "Process transcript with AI (POST /sessions/{id}/process)",
            description = """
                    Sends the session transcript to the Gemini AI model to extract user stories \
                    and acceptance criteria. The session must be in **STOPPED** or **FAILED** status.

                    The operation is synchronous — it blocks until the AI extraction is complete. \
                    On success, the session transitions to **COMPLETED** and the generated stories \
                    are returned. On infrastructure failure, the session transitions to **FAILED** \
                    and an empty story list is returned (retry is safe).""")
    @ApiResponse(responseCode = "200", description = "Extraction complete (check session.status for COMPLETED vs FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProcessTranscriptResponse.class)))
    @ApiResponse(responseCode = "422", description = "Session is not in STOPPED or FAILED status")
    @ApiResponse(responseCode = "503", description = "AI generation service not configured or unavailable")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/process", version = ApiVersioning.V1)
    ResponseEntity<ProcessTranscriptResponse> process(
            @Parameter(description = "Session to process", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "Get session transcript (GET /sessions/{id}/transcript)",
            description = "Returns the raw transcript text of the session. Separated from the main session resource per ADR-0011 (large text field).")
    @ApiResponse(responseCode = "200", description = "Transcript retrieved",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TranscriptResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/transcript", version = ApiVersioning.V1)
    ResponseEntity<TranscriptResponse> getTranscript(
            @Parameter(description = "Session to get transcript for", required = true)
            @PathVariable UUID sessionId);

    @Operation(
            summary = "List transcript segments, cursor-paginated (GET /sessions/{id}/segments)",
            description = """
                    Returns a page of the session's FINAL transcript segments as structured records so the \
                    frontend can rebuild the chat timeline of a past (potentially hours-long) session in \
                    chunks — latest first, older on scroll-up. Unlike `/transcript` (one assembled string), \
                    each segment keeps its own timing.

                    **Cursor paging:** the newest `limit` finals with `sequence < beforeSequence` are \
                    selected (omit `beforeSequence` for the newest page). To load the previous (older) \
                    chunk, pass the `sequence` of the first returned item as the next `beforeSequence`. The \
                    `segments` array is always ASCENDING by sequence for rendering; `hasMore` signals \
                    whether an older chunk remains and `totalFinalSegments` is the session-wide count.

                    Each item carries an absolute `occurredAt` instant: `session.startedAt + startMs` \
                    (falling back to the segment's persisted timestamp, then `session.createdAt + startMs`). \
                    Live partial hypotheses are excluded.""")
    @ApiResponse(responseCode = "200", description = "A cursor page of final transcript segments (may be empty)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TranscriptSegmentPageResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/segments", version = ApiVersioning.V1)
    ResponseEntity<TranscriptSegmentPageResponse> getSegments(
            @Parameter(description = "Session to get transcript segments for", required = true)
            @PathVariable UUID sessionId,
            @Parameter(description = "Cursor: return only finals with sequence < this value (omit for the newest page)", example = "1200")
            @RequestParam(required = false) Integer beforeSequence,
            @Parameter(description = "Max segments to return (default 50, capped at 200)", example = "50")
            @RequestParam(required = false) Integer limit);
}
