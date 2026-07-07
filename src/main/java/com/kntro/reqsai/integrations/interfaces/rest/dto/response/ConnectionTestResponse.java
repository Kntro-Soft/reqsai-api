package com.kntro.reqsai.integrations.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Result of testing a connection: {@code ok} plus the account display name when successful. */
@Schema(description = "Connection test result")
public record ConnectionTestResponse(boolean ok, @Nullable String accountName) {}
