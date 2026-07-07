package com.kntro.reqsai.billing.interfaces.rest.controllers;

import com.kntro.reqsai.billing.application.handler.ProcessPaymentWebhookCommandHandler;
import com.kntro.reqsai.billing.interfaces.rest.swagger.StripeWebhookController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller implementation for the Stripe webhook endpoint. Thin: delegates verification,
 * de-duplication and plan mutation to the application handler.
 */
@RestController
@RequiredArgsConstructor
public class StripeWebhookControllerImpl implements StripeWebhookController {

    private final ProcessPaymentWebhookCommandHandler processPaymentWebhookHandler;

    @Override
    public ResponseEntity<Void> handleStripe(String payload, String signature) {
        processPaymentWebhookHandler.handle(payload, signature);
        return ResponseEntity.ok().build();
    }
}
