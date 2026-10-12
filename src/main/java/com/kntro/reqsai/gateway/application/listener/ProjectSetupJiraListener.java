package com.kntro.reqsai.gateway.application.listener;

import com.kntro.reqsai.gateway.application.service.ProjectJiraSetup;
import com.kntro.reqsai.shared.application.listener.TenantAwareModuleListener;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to a project created with a Jira project to link: after the project commits, in the background and in
 * its tenant, it links the project and starts the import.
 */
@Component
@RequiredArgsConstructor
class ProjectSetupJiraListener extends TenantAwareModuleListener {

    private final ProjectJiraSetup jira;

    @ApplicationModuleListener
    void onSetupRequested(ProjectSetupRequestedIntegrationEvent event) {
        if (event.sources().jira() == null) return;
        withTenant(event, () -> jira.run(event));
    }
}
