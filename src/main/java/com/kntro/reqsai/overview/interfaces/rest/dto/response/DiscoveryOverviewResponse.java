package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Schema(description = "Discovery activity: sessions over time and how the copilot's proposals were treated")
public record DiscoveryOverviewResponse(@Schema(description = "Sessions of any status") long sessions,
        @Schema(description = "Sessions per status, all present") Map<String, Long> byStatus,
        @Schema(description = "Sessions created per week, oldest first, every week of the window present") List<WeekCountResponse> createdByWeek,
        AssistanceResponse assistance,
        @Schema(description = "Newest sessions; empty on the organization overview") List<SessionSummaryResponse> recent) {

    @Schema(description = "How analysts treated the copilot's story proposals")
    public record AssistanceResponse(@Schema(description = "Proposals accepted into the backlog") long accepted,
            @Schema(description = "Proposals dismissed") long dismissed,
            @Schema(description = "Proposals awaiting a decision") long pending,
            @Schema(description = "Clarifying questions raised") long questions,
            @Schema(description = "Share of decided proposals that were accepted (0 to 1); null until one is decided", nullable = true) @Nullable Double acceptanceRate) {
    }
}
