package com.kntro.reqsai.gateway.application.result;

import org.jspecify.annotations.Nullable;

/** Outcome of re-verifying a connection: {@code ok} plus the provider account name when successful. */
public record ConnectionTestResult(boolean ok, @Nullable String accountName) {}
