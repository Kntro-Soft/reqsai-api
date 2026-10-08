package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.ShareLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of share links (global {@code public.share_links}). */
public interface ShareLinkRepository {

    ShareLink save(ShareLink link);

    Optional<ShareLink> findByTokenHash(String tokenHash);

    Optional<ShareLink> findByIdAndProject(UUID id, UUID organizationId, UUID projectId);

    /** The project's links, newest first. */
    List<ShareLink> findAllByProject(UUID organizationId, UUID projectId);
}
