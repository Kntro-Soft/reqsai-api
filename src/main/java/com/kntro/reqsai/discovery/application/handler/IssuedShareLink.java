package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.domain.model.ShareLink;

/**
 * A freshly created share link together with its raw token. The token is not stored, so this is the
 * only moment it can be handed to the analyst.
 */
public record IssuedShareLink(ShareLink link, String token) {
}
