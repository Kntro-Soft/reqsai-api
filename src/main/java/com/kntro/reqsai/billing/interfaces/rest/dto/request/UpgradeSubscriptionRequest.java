package com.kntro.reqsai.billing.interfaces.rest.dto.request;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body to upgrade an organization's subscription to a paid plan.
 */
@Schema(description = "Request body to upgrade a subscription to a paid plan")
public record UpgradeSubscriptionRequest(

        @Schema(
                description = "Target paid plan",
                example = "PRO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PlanType planType
) {
}
