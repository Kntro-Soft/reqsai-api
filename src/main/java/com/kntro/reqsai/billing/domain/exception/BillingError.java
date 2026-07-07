package com.kntro.reqsai.billing.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes owned by the Billing & Subscription bounded context.
 */
public enum BillingError implements ErrorCatalog {

    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_PLAN_CHANGE(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_SUBSCRIPTION_STATE(HttpStatus.CONFLICT),
    PLAN_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_CONTENT),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    BillingError(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
