package com.kntro.reqsai.billing.interfaces.rest.controllers;

import com.kntro.reqsai.billing.application.handler.GetSubscriptionByOrganizationQueryHandler;
import com.kntro.reqsai.billing.application.query.GetSubscriptionByOrganizationQuery;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionResponse;
import com.kntro.reqsai.billing.interfaces.rest.mappers.response.SubscriptionResponseMapper;
import com.kntro.reqsai.billing.interfaces.rest.swagger.SubscriptionController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller implementation for subscription resource endpoints.
 */
@RestController
@RequiredArgsConstructor
public class SubscriptionControllerImpl implements SubscriptionController {

    private final GetSubscriptionByOrganizationQueryHandler getSubscriptionByOrganizationHandler;

    @Override
    @PreAuthorize("@authz.orgOwner(#organizationId, authentication)")
    public ResponseEntity<SubscriptionResponse> getByOrganization(
            UUID organizationId,
            Authentication authentication
    ) {
        Subscription subscription = getSubscriptionByOrganizationHandler.handle(
                new GetSubscriptionByOrganizationQuery(organizationId)
        );
        return ResponseEntity.ok(SubscriptionResponseMapper.toResponse(subscription));
    }
}
