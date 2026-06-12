package com.kntro.reqsai.shared.infrastructure.devtools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dev-only smoke check for the local AI stack. It exists so you can confirm chat + embeddings actually
 * reach your local models (Ollama) before the {@code discovery} bounded context wires the real pipeline.
 * <p>
 * Active only under the {@code local-ai} profile (run {@code dev,local-ai}) — that profile is what turns
 * the Ollama chat/embedding beans on, so this controller never loads without them. It requires a JWT
 * like any endpoint; mint one in dev via {@code GET /api/v1/auth/dev-token}. Throwaway scaffolding;
 * {@code discovery} replaces it.
 * <p>
 * {@code GET /api/v1/ai/ping} → calls the active chat + embedding models and reports what came back.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Profile("local-ai")
@RequiredArgsConstructor
@Slf4j
public class AiPingController {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        String prompt = "Reply with exactly one word: pong";
        try {
            String reply = chatModel.call(prompt);
            float[] embedding = embeddingModel.embed("reqs-ai local embedding check");
            return Map.of(
                    "ok", true,
                    "chatPrompt", prompt,
                    "chatReply", reply.strip(),
                    "embeddingDimensions", embedding.length);
        } catch (Exception e) {
            log.warn("AI ping failed", e);
            return Map.of(
                    "ok", false,
                    "error", "AI call failed — is the provider reachable? (dev defaults to Ollama at "
                            + "http://localhost:11434; check `ollama serve` and the pulled models)",
                    "detail", String.valueOf(e.getMessage()));
        }
    }
}
