package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/projects/{projectId}/sessions}.
 *
 * @param title    descriptive session title
 * @param language BCP-47 meeting language (e.g. {@code es-PE})
 */
public record CreateDiscoverySessionRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 8)
        String language
) {
}
