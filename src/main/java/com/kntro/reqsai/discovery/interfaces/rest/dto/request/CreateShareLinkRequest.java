package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.ShareAccess;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "A link that lets a client review the project's stories or follow a session without an account")
public record CreateShareLinkRequest(

        @Schema(description = "Days the link stays valid; 14 when omitted", example = "14",
                minimum = "1", maximum = "90", nullable = true)
        @Min(1) @Max(90)
        @Nullable Integer days,
        @Schema(description = "Discovery session the link follows; omit to share the project's stories", nullable = true)
        @Nullable UUID sessionId,
        @Schema(description = "What the visitor may do on a session link: VIEW (only follow along) or REVIEW "
                + "(also accept, dismiss, approve, reject); VIEW when omitted", nullable = true,
                allowableValues = {"VIEW", "REVIEW"})
        @Nullable ShareAccess access
) {

    public static final int DEFAULT_DAYS = 14;

    public ShareAccess accessOrDefault() {
        return access == null ? ShareAccess.VIEW : access;
    }

    public int daysOrDefault() {
        return days == null ? DEFAULT_DAYS : days;
    }
}
