package com.kntro.reqsai.shared.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/** Utilities for handling multipart file uploads in REST controllers. */
public final class FileUploadUtils {

    private FileUploadUtils() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Reads all bytes from a multipart file. Throws {@code 422 Unprocessable Entity} if the
     * upload stream cannot be read — indicating a client-side transmission failure.
     */
    public static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Failed to read uploaded file '" + file.getOriginalFilename() + "': " + e.getMessage());
        }
    }
}
