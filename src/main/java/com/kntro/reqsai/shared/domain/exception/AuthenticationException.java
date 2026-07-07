package com.kntro.reqsai.shared.domain.exception;

/**
 * Authentication failure (bad credentials, invalid/expired token, missing auth) — HTTP 401.
 * A distinct type so the handler can react to auth failures specifically.
 */
public class AuthenticationException extends DomainException {

    public AuthenticationException(ErrorCatalog error, String message) {
        super(error, message);
    }
}
