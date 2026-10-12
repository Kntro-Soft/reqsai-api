package com.kntro.reqsai.codebase.application.listener;

import com.kntro.reqsai.codebase.application.service.ProjectCodeSetup;
import com.kntro.reqsai.shared.application.listener.TenantAwareModuleListener;
import com.kntro.reqsai.workspace.api.ProjectSetupRequestedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to a project created with repositories: after the project commits, in the background and in its tenant,
 * it connects them and starts indexing.
 */
@Component
@RequiredArgsConstructor
class ProjectSetupCodeListener extends TenantAwareModuleListener {

    private final ProjectCodeSetup code;

    @ApplicationModuleListener
    void onSetupRequested(ProjectSetupRequestedIntegrationEvent event) {
        if (event.sources().github().isEmpty()) return;
        withTenant(event, () -> code.run(event));
    }
}
