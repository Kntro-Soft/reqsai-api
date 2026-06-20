package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record AddGlossaryTermCommand(UUID organizationId, UUID projectId, String term, String definition, UUID requestedBy) {}
