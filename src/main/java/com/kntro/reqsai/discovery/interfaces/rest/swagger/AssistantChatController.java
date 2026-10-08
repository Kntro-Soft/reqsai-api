package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.SendAssistantMessageRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AssistantExchangeResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AssistantMessageResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * API contract for a project's assistant chat, implemented by {@code controllers.AssistantChatControllerImpl}.
 * The analyst types to ReqsAI from the capture page, with or without a live session: questions about the
 * project are answered, requirements become suggestions to review. Tenant-scoped through the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/assistant/messages",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assistant chat", description = "Text conversation with ReqsAI about a project")
public interface AssistantChatController {

    @Operation(
            summary = "Send a message to the assistant",
            description = """
                    Stores the analyst's message and ReqsAI's reply. A question about the project (backlog, \
                    glossary, constraints, sessions) is answered from the project's data. A requirement goes \
                    through the regular suggestion pipeline (duplicate detection, targeting existing stories) \
                    and comes back as suggestions in the reply; they belong to the project but to no session, \
                    and are reviewed with POST /projects/{projectId}/suggestions/{suggestionId}/accept|dismiss. \
                    Requires SESSION_RUN. Answers 422 REQUIREMENT_GENERATION_FAILED when the AI is unavailable.""")
    @ApiResponse(responseCode = "201", description = "The analyst's message and the reply",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AssistantExchangeResponse.class)))
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, version = ApiVersioning.V1)
    ResponseEntity<AssistantExchangeResponse> send(
            @Parameter(description = "Project of the chat", required = true) @PathVariable UUID projectId,
            @Valid @RequestBody SendAssistantMessageRequest request);

    @Operation(
            summary = "List the assistant chat",
            description = """
                    The newest messages of the project's assistant chat, oldest first. Each reply carries the \
                    suggestions it raised in their current state. Requires SESSION_READ.""")
    @ApiResponse(responseCode = "200", description = "Chat messages, oldest first")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<AssistantMessageResponse>> list(
            @Parameter(description = "Project of the chat", required = true) @PathVariable UUID projectId,
            @Parameter(description = "How many of the newest messages to return (1-100)", example = "50")
            @RequestParam(required = false, defaultValue = "50") int limit);
}
