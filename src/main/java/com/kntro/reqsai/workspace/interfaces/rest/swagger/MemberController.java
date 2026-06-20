package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MemberResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface MemberController {
    @ApiResponseBadRequest @ApiStandardErrorResponses
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<MemberResponse> createMember(@PathVariable UUID orgId, @Valid @RequestBody CreateMemberRequest request, Authentication authentication);

    @ApiStandardErrorResponses
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<MemberResponse>> listMembers(@PathVariable UUID orgId, Authentication authentication);

    @ApiResponseNotFound @ApiStandardErrorResponses
    @GetMapping(value = "/{memberId}", version = ApiVersioning.V1)
    ResponseEntity<MemberResponse> getMember(@PathVariable UUID orgId, @PathVariable UUID memberId, Authentication authentication);

    @ApiResponseNotFound @ApiStandardErrorResponses
    @DeleteMapping(value = "/{memberId}", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteMember(@PathVariable UUID orgId, @PathVariable UUID memberId, Authentication authentication);
}
