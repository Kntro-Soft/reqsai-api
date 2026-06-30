package com.kntro.reqsai.shared.application.avatar;

import java.util.Optional;

/**
 * Port for downloading a deterministic avatar image from an external generator. Implemented by an adapter
 * in {@code infrastructure}; the application layer depends only on this.
 * <p>
 * Fail-soft: {@link #download(String)} returns an empty {@link Optional} on any failure so the calling
 * creation handler simply leaves the entity without an avatar — a missing avatar must never abort creation.
 */
public interface AvatarDownloadPort {

    Optional<GeneratedAvatar> download(String url);
}
