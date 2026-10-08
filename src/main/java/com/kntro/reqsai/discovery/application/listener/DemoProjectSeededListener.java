package com.kntro.reqsai.discovery.application.listener;

import com.kntro.reqsai.discovery.application.service.DemoDiscoveryContentSeeder;
import com.kntro.reqsai.workspace.api.DemoProjectSeededIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds Discovery's part of the demo project whenever Workspace (re)seeds it — on onboarding and on
 * "restore demo data" (US28).
 * <p>
 * A plain synchronous {@link EventListener}, on purpose: the event is published inside Workspace's seeding
 * transaction with the tenant bound, so this runs in that same transaction and thread. A failure here
 * rolls the whole demo (re)seed back, and the restore endpoint answers only once the sample sessions and
 * stories are in place — the UI can reload right away.
 */
@Component
@RequiredArgsConstructor
class DemoProjectSeededListener {

    private final DemoDiscoveryContentSeeder seeder;

    @EventListener
    void onDemoProjectSeeded(DemoProjectSeededIntegrationEvent event) {
        seeder.reseed(event.projectId());
    }
}
