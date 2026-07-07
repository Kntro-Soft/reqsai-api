package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record ListMembersQuery(
        UUID organizationId,
        UUID requestedBy
) {}
