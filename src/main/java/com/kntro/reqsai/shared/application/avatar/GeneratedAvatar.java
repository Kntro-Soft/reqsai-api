package com.kntro.reqsai.shared.application.avatar;

/**
 * The bytes of a generated avatar together with their content type. A neutral cross-layer carrier:
 * produced by the infrastructure download adapter, returned by application query handlers, and rendered
 * by the interfaces layer — so it lives in the application layer, importable by all without touching
 * {@code ..infrastructure..}.
 */
public record GeneratedAvatar(byte[] bytes, String contentType) {
}
