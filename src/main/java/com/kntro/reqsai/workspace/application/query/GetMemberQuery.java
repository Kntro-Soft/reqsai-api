package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetMemberQuery(
        UUID organizationId,
        UUID memberId,
        UUID requestedBy
) {}
