package com.kntro.reqsai.codebase.interfaces.rest.controllers;

import com.kntro.reqsai.codebase.application.service.GitHubWebhookService;
import com.kntro.reqsai.codebase.interfaces.rest.swagger.GitHubWebhookController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implementation of the {@link GitHubWebhookController}: verifies the signature, then applies the event. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookControllerImpl implements GitHubWebhookController {

    private final GitHubWebhookService webhooks;

    @Override
    public ResponseEntity<Void> handle(byte[] payload, String event, String signature, String delivery) {
        if (!webhooks.verify(payload, signature)) {
            log.warn("GitHub webhook {} ({}) rejected: invalid signature", delivery, event);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhooks.handle(event, payload);
        return ResponseEntity.accepted().build();
    }
}
