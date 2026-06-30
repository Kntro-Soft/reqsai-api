package com.kntro.reqsai.shared.infrastructure.avatar;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Builds the {@link ResponseEntity} for the public avatar serve endpoints.
 * <p>
 * Returns the stored bytes with their content type (defaulting to {@code image/svg+xml}),
 * {@code Cache-Control: public, max-age=86400}, and a content-hash {@code ETag}. A missing avatar
 * (empty {@link Optional} or {@code null}/empty bytes) yields {@code 404 Not Found}.
 */
public final class AvatarResponses {

    private static final MediaType DEFAULT_CONTENT_TYPE = MediaType.valueOf("image/svg+xml");
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private AvatarResponses() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ResponseEntity<byte[]> of(Optional<GeneratedAvatar> avatar) {
        return avatar
                .filter(a -> a.bytes() != null && a.bytes().length > 0)
                .map(AvatarResponses::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> ok(GeneratedAvatar avatar) {
        MediaType contentType = avatar.contentType() != null
                ? MediaType.parseMediaType(avatar.contentType())
                : DEFAULT_CONTENT_TYPE;
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .eTag(etag(avatar.bytes()))
                .body(avatar.bytes());
    }

    private static String etag(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return Long.toHexString(crc.getValue());
    }
}
