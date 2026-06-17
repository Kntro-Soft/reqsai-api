package com.kntro.reqsai.shared.infrastructure.devtools;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dev-only smoke check for the AI stack (chat + embeddings). Always active under {@code dev};
 * reports {@code ok:false} if no AI provider is configured (beans absent), so it degrades gracefully
 * when AI vars are not set in {@code .env}. Requires a JWT — mint one via
 * {@code GET /api/auth/dev-token}. Throwaway scaffolding; {@code discovery} replaces it.
 * <p>
 * {@code GET /api/ai/ping} → calls the active chat + embedding models and reports what came back.
 */
@RestController
@RequestMapping("/api/ai")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class AiPingController {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @GetMapping(value = "/ping", version = ApiVersioning.V1)
    public Map<String, Object> ping() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();

        if (chatModel == null || embeddingModel == null) {
            return Map.of(
                    "ok", false,
                    "reason", "AI not configured — set SPRING_AI_MODEL_CHAT / SPRING_AI_MODEL_EMBEDDING "
                            + "and the corresponding API key or base URL in your .env");
        }

        String prompt = "Reply with exactly one word: pong";
        try {
            String reply = chatModel.call(prompt);
            float[] embedding = embeddingModel.embed("reqs-ai local embedding check");
            return Map.of(
                    "ok", true,
                    "chatPrompt", prompt,
                    "chatReply", reply != null ? reply.strip() : "",
                    "embeddingDimensions", embedding.length);
        } catch (Exception e) {
            log.warn("AI ping failed", e);
            return Map.of(
                    "ok", false,
                    "error", "AI call failed — is the provider reachable? "
                            + "Check OLLAMA_BASE_URL / GEMINI_API_KEY / OPENAI_API_KEY in your .env",
                    "detail", String.valueOf(e.getMessage()));
        }
    }
}
