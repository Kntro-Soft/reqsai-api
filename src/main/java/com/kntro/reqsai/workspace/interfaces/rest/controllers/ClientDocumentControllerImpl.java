package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.shared.interfaces.rest.FileUploadUtils;
import com.kntro.reqsai.workspace.application.command.AnalyzeClientDocumentCommand;
import com.kntro.reqsai.workspace.application.handler.AnalyzeClientDocumentCommandHandler;
import com.kntro.reqsai.workspace.application.handler.ApplyClientDocumentCommandHandler;
import com.kntro.reqsai.workspace.application.result.ClientDocumentAnalysis;
import com.kntro.reqsai.workspace.application.result.ClientDocumentApplyResult;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ApplyClientDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ClientDocumentAnalysisResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ClientDocumentApplyResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ClientDocumentRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ClientDocumentResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ClientDocumentController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ClientDocumentControllerImpl implements ClientDocumentController {

    private final AnalyzeClientDocumentCommandHandler analyzeClientDocument;
    private final ApplyClientDocumentCommandHandler applyClientDocument;

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'DOCUMENT_CREATE', authentication)")
    public ResponseEntity<ClientDocumentAnalysisResponse> uploadDocument(
            UUID orgId, UUID projectId, MultipartFile file, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ClientDocumentAnalysis analysis = analyzeClientDocument.handle(new AnalyzeClientDocumentCommand(
                orgId, projectId, file.getOriginalFilename(), file.getContentType(),
                FileUploadUtils.readBytes(file), requestedBy));
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientDocumentResponseMapper.toResponse(analysis));
    }

    @Override
    @PreAuthorize("@authz.projectPermission(#orgId, #projectId, 'DOCUMENT_CREATE', authentication)")
    public ResponseEntity<ClientDocumentApplyResponse> applyDocument(
            UUID orgId, UUID projectId, UUID documentId, ApplyClientDocumentRequest request,
            Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ClientDocumentApplyResult result = applyClientDocument.handle(
                ClientDocumentRequestMapper.toCommand(orgId, projectId, documentId, request, requestedBy));
        return ResponseEntity.ok(ClientDocumentResponseMapper.toResponse(result));
    }
}
