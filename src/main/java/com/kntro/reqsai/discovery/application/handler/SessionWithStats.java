package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.SessionStatsRepository.SessionStats;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;

/**
 * Application-layer projection pairing a {@link DiscoverySession} with its history-table
 * {@link SessionStats}, returned by the get/list session query handlers so the REST layer can render
 * the stats without a second round trip or an N+1.
 */
public record SessionWithStats(DiscoverySession session, SessionStats stats) {
}
