package com.kntro.reqsai.iam.interfaces.rest.mappers.request;

import com.kntro.reqsai.iam.application.command.AuthenticateCommand;
import com.kntro.reqsai.iam.application.command.RegisterAccountCommand;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.LoginRequest;
import com.kntro.reqsai.iam.interfaces.rest.dto.request.RegisterRequest;

/** Maps inbound auth request DTOs to application commands. */
public final class AuthRequestMapper {

    private AuthRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static RegisterAccountCommand toCommand(RegisterRequest request) {
        return new RegisterAccountCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName());
    }

    public static AuthenticateCommand toCommand(LoginRequest request) {
        return new AuthenticateCommand(request.email(), request.password());
    }
}
