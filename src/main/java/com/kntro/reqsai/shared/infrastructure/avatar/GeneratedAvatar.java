package com.kntro.reqsai.shared.infrastructure.avatar;

/**
 * The downloaded bytes of a generated avatar together with their content type. Stored verbatim on the
 * owning aggregate ({@code avatar} / {@code avatar_content_type}) and served back unchanged.
 */
public record GeneratedAvatar(byte[] bytes, String contentType) {
}
