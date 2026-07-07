package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * API contract (OpenAPI documentation) for the organization avatar endpoints. The implementation lives in
 * {@code controllers.OrganizationAvatarControllerImpl}.
 * <p>
 * The GET is public on purpose: the bytes are loaded by browser {@code <img src>} tags, which cannot
 * carry the Bearer token. The image is a non-sensitive generated gradient and the organization id is an
 * unguessable UUIDv7. Whitelisted in {@code SecurityConfiguration}. The PUT upload is authenticated and
 * restricted to the organization owner or an admin member.
 */
@RequestMapping(path = ApiVersioning.BASE + "/organizations")
@Tag(name = "Organizations", description = "Organization registry and tenant schema provisioning")
public interface OrganizationAvatarController {

    @Operation(
            summary = "Get an organization's avatar",
            description = "Returns the organization's generated avatar image bytes. Public — no authentication required.")
    @ApiResponse(
            responseCode = "200",
            description = "Avatar image",
            content = @Content(schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Organization has no avatar", content = @Content)
    @GetMapping(path = "/{orgId}/avatar", version = ApiVersioning.V1)
    ResponseEntity<byte[]> getAvatar(@Parameter(description = "Organization UUID") @PathVariable UUID orgId);

    @Operation(
            summary = "Upload an organization's avatar",
            description = """
                    Replaces the organization's avatar with an uploaded image (svg, png, jpeg or webp,
                    max 1 MB). Only the organization owner or an active admin member may do so.""")
    @ApiResponse(
            responseCode = "200",
            description = "Avatar updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrganizationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing, oversized or non-image file", content = @Content)
    @ApiResponse(responseCode = "404", description = "Organization not found", content = @Content)
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/{orgId}/avatar", version = ApiVersioning.V1, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<OrganizationResponse> uploadAvatar(
            @Parameter(description = "Organization UUID") @PathVariable UUID orgId,
            @Parameter(description = "Image file (svg, png, jpeg, webp; max 1 MB)", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication);
}
