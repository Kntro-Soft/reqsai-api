package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A message the analyst types to ReqsAI in the project's assistant chat")
public record SendAssistantMessageRequest(

        @Schema(description = "A question about the project, or a requirement to turn into a suggestion",
                example = "Quiero que el cliente pueda cancelar su reserva hasta 2 horas antes",
                minLength = 1, maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 2000)
        String content
) {
}
