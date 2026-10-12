package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AcceptSharedSuggestionCommand;
import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.application.command.DismissSharedSuggestionCommand;
import com.kntro.reqsai.discovery.application.command.DismissSuggestionCommand;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lets a reviewer with a session link accept or dismiss the session's suggestions. The decision is the
 * analyst's own one ({@link AcceptSuggestionCommandHandler}, {@link DismissSuggestionCommandHandler}); this
 * only checks that the link may decide and pins the work to the link's session.
 */
@Component
@RequiredArgsConstructor
public class DecideSharedSuggestionCommandHandler {

    private final SharedLinkGateway gateway;
    private final AcceptSuggestionCommandHandler accept;
    private final DismissSuggestionCommandHandler dismiss;

    public Suggestion handle(AcceptSharedSuggestionCommand command) {
        return gateway.withActiveSessionLink(command.token(), link -> {
            requireReviewer(link);
            var inSession = command.accept();
            return accept.handle(new AcceptSuggestionCommand(
                    link.getSessionId(), command.suggestionId(), inSession.editedTitle(), inSession.editedRole(),
                    inSession.editedAction(), inSession.editedBenefit(), inSession.editedPriority(),
                    inSession.editedStoryPoints(), inSession.editedAcceptanceCriteria(), inSession.approve()));
        });
    }

    public Suggestion handle(DismissSharedSuggestionCommand command) {
        return gateway.withActiveSessionLink(command.token(), link -> {
            requireReviewer(link);
            return dismiss.handle(new DismissSuggestionCommand(
                    link.getSessionId(), command.suggestionId()));
        });
    }

    private static void requireReviewer(ShareLink link) {
        if (!link.canReview()) {
            throw DiscoveryExceptions.shareLinkReadOnly();
        }
    }
}
