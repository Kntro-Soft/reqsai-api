package com.kntro.reqsai.discovery.infrastructure.ai.improvement;

import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.StoryImprovementResult;
import com.kntro.reqsai.discovery.application.port.UnparseableGenerationException;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the model's reply defensively: tolerates code fences and prose around the object, a proposal given at
 * the root instead of under {@code proposal}, and missing optional parts. What is not the contract (no JSON object,
 * no proposal, a criterion that is not an object) is an {@link UnparseableGenerationException}. It only reads the
 * structure; the limits and the pending-value contract are checked afterwards, whatever the provider, by
 * {@code StoryImprovementNormalizer}.
 */
final class StoryImprovementParser {

    private StoryImprovementParser() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    static StoryImprovementResult parse(ObjectMapper mapper, @Nullable String reply) {
        String json = reply == null ? "" : reply.strip();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw unparseable("no JSON object in the reply", null);
        }
        try {
            JsonNode root = mapper.readTree(json.substring(start, end + 1));
            JsonNode proposal = root.path("proposal").isObject() ? root.path("proposal") : root;
            if (!proposal.has("title") && !proposal.has("action")) {
                throw unparseable("the reply has no proposal", null);
            }
            List<StoryDraft.CriterionDraft> criteria = new ArrayList<>();
            JsonNode criteriaNode = proposal.path("acceptanceCriteria");
            if (criteriaNode.isArray()) {
                for (JsonNode node : criteriaNode) {
                    if (!node.isObject()) {
                        throw unparseable("an acceptance criterion is not an object", null);
                    }
                    criteria.add(new StoryDraft.CriterionDraft(text(node.path("scenario")),
                            blank(text(node.path("given"))), blank(text(node.path("when"))),
                            blank(text(node.path("then")))));
                }
            }
            StoryDraft draft = new StoryDraft(blank(text(proposal.path("title"))), blank(text(proposal.path("role"))),
                    blank(text(proposal.path("action"))), blank(text(proposal.path("benefit"))), criteria);

            List<StoryImprovementResult.PendingValue> pending = new ArrayList<>();
            for (JsonNode node : root.path("pendingValues")) {
                String token = node.isObject() ? text(node.path("token")) : text(node);
                if (token != null) {
                    pending.add(new StoryImprovementResult.PendingValue(token,
                            node.isObject() ? blank(text(node.path("hint"))) : ""));
                }
            }
            List<String> fixes = new ArrayList<>();
            for (JsonNode node : root.path("fixes")) {
                String fix = text(node);
                if (fix != null) {
                    fixes.add(fix);
                }
            }
            return new StoryImprovementResult(draft, pending, fixes);
        } catch (JacksonException e) {
            throw unparseable("invalid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private static @Nullable String text(@Nullable JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blank(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static UnparseableGenerationException unparseable(String reason, @Nullable Throwable cause) {
        return DiscoveryInfrastructureExceptions.generationOutputUnparseable("Invalid story improvement: " + reason, cause);
    }
}
