package com.kntro.reqsai.billing.interfaces.rest.controllers;

import com.kntro.reqsai.billing.application.command.CancelSubscriptionCommand;
import com.kntro.reqsai.billing.application.command.ReactivateSubscriptionCommand;
import com.kntro.reqsai.billing.application.command.UpgradeSubscriptionCommand;
import com.kntro.reqsai.billing.application.handler.CancelSubscriptionCommandHandler;
import com.kntro.reqsai.billing.application.handler.GetSubscriptionByOrganizationQueryHandler;
import com.kntro.reqsai.billing.application.handler.GetSubscriptionUsageQueryHandler;
import com.kntro.reqsai.billing.application.handler.PlanChangeOutcome;
import com.kntro.reqsai.billing.application.handler.ReactivateSubscriptionCommandHandler;
import com.kntro.reqsai.billing.application.handler.UpgradeSubscriptionCommandHandler;
import com.kntro.reqsai.billing.application.query.GetSubscriptionByOrganizationQuery;
import com.kntro.reqsai.billing.application.query.GetSubscriptionUsageQuery;
import com.kntro.reqsai.billing.application.query.SubscriptionUsageView;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.interfaces.rest.dto.request.UpgradeSubscriptionRequest;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.PlanChangeResponse;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionResponse;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionUsageResponse;
import com.kntro.reqsai.billing.interfaces.rest.mappers.response.PlanChangeResponseMapper;
import com.kntro.reqsai.billing.interfaces.rest.mappers.response.SubscriptionResponseMapper;
import com.kntro.reqsai.billing.interfaces.rest.mappers.response.SubscriptionUsageResponseMapper;
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
    private final GetSubscriptionUsageQueryHandler getSubscriptionUsageHandler;
    private final UpgradeSubscriptionCommandHandler upgradeSubscriptionHandler;
    private final CancelSubscriptionCommandHandler cancelSubscriptionHandler;
    private final ReactivateSubscriptionCommandHandler reactivateSubscriptionHandler;

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

    @Override
    @PreAuthorize("@authz.orgOwner(#organizationId, authentication)")
    public ResponseEntity<SubscriptionUsageResponse> getUsage(
            UUID organizationId,
            Authentication authentication
    ) {
        SubscriptionUsageView view = getSubscriptionUsageHandler.handle(
                new GetSubscriptionUsageQuery(organizationId)
        );
        return ResponseEntity.ok(SubscriptionUsageResponseMapper.toResponse(view));
    }

    @Override
    @PreAuthorize("@authz.orgOwner(#organizationId, authentication)")
    public ResponseEntity<PlanChangeResponse> upgrade(
            UUID organizationId,
            UpgradeSubscriptionRequest request,
            Authentication authentication
    ) {
        PlanChangeOutcome outcome = upgradeSubscriptionHandler.handle(
                new UpgradeSubscriptionCommand(organizationId, request.planType())
        );
        return ResponseEntity.ok(PlanChangeResponseMapper.toResponse(outcome));
    }

    @Override
    @PreAuthorize("@authz.orgOwner(#organizationId, authentication)")
    public ResponseEntity<SubscriptionResponse> cancel(
            UUID organizationId,
            Authentication authentication
    ) {
        Subscription subscription = cancelSubscriptionHandler.handle(
                new CancelSubscriptionCommand(organizationId)
        );
        return ResponseEntity.ok(SubscriptionResponseMapper.toResponse(subscription));
    }

    @Override
    @PreAuthorize("@authz.orgOwner(#organizationId, authentication)")
    public ResponseEntity<SubscriptionResponse> reactivate(
            UUID organizationId,
            Authentication authentication
    ) {
        Subscription subscription = reactivateSubscriptionHandler.handle(
                new ReactivateSubscriptionCommand(organizationId)
        );
        return ResponseEntity.ok(SubscriptionResponseMapper.toResponse(subscription));
    }
}
