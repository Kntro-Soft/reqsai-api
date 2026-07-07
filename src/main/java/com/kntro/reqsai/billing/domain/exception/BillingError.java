package com.kntro.reqsai.billing.domain.exception;

import com.kntro.reqsai.shared.domain.exception.ErrorCatalog;
import org.springframework.http.HttpStatus;

/**
 * Error codes owned by the Billing & Subscription bounded context.
 */
public enum BillingError implements ErrorCatalog {

    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_EXISTS(HttpStatus.CONFLICT);

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
