package com.kntro.reqsai.shared.infrastructure.avatar;

import com.kntro.reqsai.shared.application.avatar.GeneratedAvatar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Downloads a deterministic avatar image from an external generator (e.g. {@code avatar.vercel.sh} or
 * {@code api.dicebear.com}) and returns its bytes and content type.
 * <p>
 * Used by the org/project/user creation handlers <em>after</em> the aggregate is persisted (the id is the
 * seed). A download failure is never fatal: {@link #download(String)} returns an empty {@link Optional} so
 * the entity is simply left without an avatar — callers must not let a missing avatar abort creation.
 */
@Component
@Slf4j
public class AvatarDownloadAdapter {

    private static final String DEFAULT_CONTENT_TYPE = MediaType.valueOf("image/svg+xml").toString();

    private final RestClient restClient;

    public AvatarDownloadAdapter() {
        this.restClient = RestClient.create();
    }

    /**
     * Downloads the avatar at {@code url}. Returns {@link Optional#empty()} on any failure (network error,
     * non-2xx status, empty body) after logging a warning — entity creation must continue regardless.
     */
    public Optional<GeneratedAvatar> download(String url) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.warn("Avatar download from {} returned an empty body", url);
                return Optional.empty();
            }
            MediaType mediaType = response.getHeaders().getContentType();
            String contentType = mediaType != null ? mediaType.toString() : DEFAULT_CONTENT_TYPE;
            log.debug("Downloaded avatar from {} ({} bytes, {})", url, body.length, contentType);
            return Optional.of(new GeneratedAvatar(body, contentType));
        } catch (Exception e) {
            log.warn("Failed to download avatar from {} — leaving avatar empty: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
