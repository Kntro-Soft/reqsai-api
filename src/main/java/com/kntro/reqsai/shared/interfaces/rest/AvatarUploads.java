package com.kntro.reqsai.shared.interfaces.rest;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Validates a multipart avatar upload and turns it into a {@link GeneratedAvatar} (bytes + content type).
 * <p>
 * Rejects with {@code 400 Bad Request} when the file is missing/empty, exceeds {@link #MAX_BYTES}, or is
 * not one of the {@linkplain #ALLOWED_CONTENT_TYPES allowed image types} — so a user can only replace a
 * generated avatar with another reasonable image.
 */
public final class AvatarUploads {

    /** Maximum accepted avatar upload size (1 MB). */
    public static final long MAX_BYTES = 1L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/svg+xml",
            "image/png",
            "image/jpeg",
            "image/webp");

    private AvatarUploads() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Validates the upload and returns its bytes with a normalised content type. */
    public static GeneratedAvatar validated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Avatar file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw badRequest("Avatar file exceeds the maximum size of " + MAX_BYTES + " bytes");
        }
        String contentType = normalize(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw badRequest("Unsupported avatar content type: " + file.getContentType()
                    + " (allowed: " + ALLOWED_CONTENT_TYPES + ")");
        }
        byte[] bytes = FileUploadUtils.readBytes(file);
        if (bytes.length == 0) {
            throw badRequest("Avatar file is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw badRequest("Avatar file exceeds the maximum size of " + MAX_BYTES + " bytes");
        }
        return new GeneratedAvatar(bytes, contentType);
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String base = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return base.strip().toLowerCase();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
