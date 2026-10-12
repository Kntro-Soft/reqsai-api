package com.kntro.reqsai.codebase.interfaces.rest.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * GitHub App webhooks. Authenticated by the {@code X-Hub-Signature-256} HMAC of the raw body with the App's
 * webhook secret, not by JWT: the caller is GitHub. The body is bound as bytes, verbatim, for the signature.
 */
@RequestMapping(path = "/api/code/webhooks")
@Tag(name = "Code Webhooks", description = "GitHub App webhook callbacks (signature-verified)")
public interface GitHubWebhookController {

    @Operation(summary = "GitHub App webhook receiver",
            description = """
                    push: reindexes the connected repositories that track the pushed branch. installation \
                    (deleted, suspend, unsuspend) and installation_repositories (removed): stops reading what \
                    the account no longer shares. Other events are acknowledged and ignored. Not for \
                    interactive use.""")
    @ApiResponse(responseCode = "202", description = "Event accepted (or ignored)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid signature")
    @PostMapping(path = "/github")
    ResponseEntity<Void> handle(
            @RequestBody byte[] payload,
            @RequestHeader(name = "X-GitHub-Event", required = false) String event,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String delivery);
}
