package com.kntro.reqsai.discovery.interfaces.rest.mappers.request;

import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AcceptSuggestionRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Maps the inbound accept-suggestion request DTO to its application command. */
public final class AcceptSuggestionRequestMapper {

    private AcceptSuggestionRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Builds the command. A missing body yields a command with all overrides {@code null}
     * ("accept the draft as-is").
     */
    public static AcceptSuggestionCommand toCommand(UUID sessionId, UUID suggestionId,
                                                    @Nullable AcceptSuggestionRequest request) {
        if (request == null) {
            return new AcceptSuggestionCommand(sessionId, suggestionId,
                    null, null, null, null, null, null, null);
        }
        List<AcceptSuggestionCommand.Criterion> criteria = request.editedAcceptanceCriteria() == null ? null
                : request.editedAcceptanceCriteria().stream()
                        .map(c -> new AcceptSuggestionCommand.Criterion(c.scenario(), c.given(), c.when(), c.then()))
                        .toList();
        return new AcceptSuggestionCommand(sessionId, suggestionId,
                request.editedTitle(), request.editedRole(), request.editedAction(), request.editedBenefit(),
                request.editedPriority(), request.editedStoryPoints(), criteria);
    }
}
