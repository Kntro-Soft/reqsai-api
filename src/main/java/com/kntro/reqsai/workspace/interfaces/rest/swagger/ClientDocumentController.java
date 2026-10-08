package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ApplyClientDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ClientDocumentAnalysisResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ClientDocumentApplyResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/documents",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Client Documents", description = "Upload client documents (PDF/Word) and turn them into project context")
public interface ClientDocumentController {

    @Operation(
            summary = "Upload and analyze a client document",
            description = """
                    Uploads a PDF (.pdf) or Word (.docx) document of the client, extracts its text and asks the AI
                    to classify it into a project context summary, glossary terms and project constraints.

                    - The type is checked by extension, declared content type and magic bytes: executables and
                      any other format are rejected with 415 `DOCUMENT_TYPE_NOT_ALLOWED`
                    - Files over 50 MB are rejected with 413 (`DOCUMENT_TOO_LARGE`, or `PAYLOAD_TOO_LARGE` when the
                      multipart limit stops the request first)
                    - An empty file or one without extractable text (a scan) is 422 `DOCUMENT_EMPTY`; a damaged
                      or password-protected one is 422 `DOCUMENT_UNREADABLE`
                    - The text is capped at 200,000 characters (`truncated`); the AI reads its first 60,000
                    - The document is stored as PENDING; apply or discard (DELETE) it after the review
                    - When no AI model is available the document is still stored, with `classified: false`,
                      empty suggestions and an excerpt as `context`""")
    @ApiResponse(
            responseCode = "201",
            description = "Document analyzed; review the proposal and apply it",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ClientDocumentAnalysisResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "documentId": "019756a0-1234-7abc-8def-000000000601",
                              "fileName": "Términos de referencia.pdf",
                              "mediaType": "application/pdf",
                              "sizeBytes": 184320,
                              "extractedChars": 18452,
                              "truncated": false,
                              "classified": true,
                              "documentType": "TECHNICAL_SPEC",
                              "context": "La Tradición es una cadena de restaurantes de Lima que busca...",
                              "glossary": [
                                { "term": "Comensal", "definition": "Cliente que reserva una mesa.", "exists": false }
                              ],
                              "constraints": [
                                { "text": "Debe cumplir la Ley 29733 de protección de datos personales.", "exists": false }
                              ]
                            }""")))
    @ApiResponse(responseCode = "413", description = "File over 50 MB", content = @Content)
    @ApiResponse(responseCode = "415", description = "Not a PDF or Word (.docx) document", content = @Content)
    @ApiResponse(responseCode = "422", description = "Empty, unreadable or document plan limit reached", content = @Content)
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/upload", version = ApiVersioning.V1, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ClientDocumentAnalysisResponse> uploadDocument(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "The PDF or Word (.docx) file, at most 50 MB") @RequestParam("file") MultipartFile file,
            Authentication authentication);

    @Operation(
            summary = "Apply a reviewed client document",
            description = """
                    Keeps an analyzed (PENDING) client document as project context and adds the selected glossary
                    terms and constraints.

                    - Terms the glossary already has and constraints the project already records are skipped
                    - Adding terms needs GLOSSARY_TERM_WRITE and adding constraints CONSTRAINT_WRITE
                    - The document becomes ACTIVE; its summary is given to the AI as project context
                    - A document that is no longer pending is 409 `PROJECT_DOCUMENT_NOT_PENDING`""")
    @ApiResponse(responseCode = "200", description = "Document applied",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ClientDocumentApplyResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/{documentId}/apply", version = ApiVersioning.V1, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ClientDocumentApplyResponse> applyDocument(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Pending document UUID") @PathVariable UUID documentId,
            @Valid @RequestBody ApplyClientDocumentRequest request,
            Authentication authentication);
}
