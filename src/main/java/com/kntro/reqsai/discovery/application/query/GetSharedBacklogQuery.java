package com.kntro.reqsai.discovery.application.query;

/** What a client sees when opening a share link, identified by its raw token. */
public record GetSharedBacklogQuery(String token) {
}
