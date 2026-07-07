package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.port.AccountRepository;
import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.query.GetAuthenticatedUserQuery;
import com.kntro.reqsai.iam.application.result.UserProfile;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.Account;
import com.kntro.reqsai.iam.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads the authenticated user's profile (with its account email) by id. */
@Component
@RequiredArgsConstructor
public class GetAuthenticatedUserQueryHandler {

    private final UserRepository users;
    private final AccountRepository accounts;

    @Transactional(readOnly = true)
    public UserProfile handle(GetAuthenticatedUserQuery query) {
        User user = users.findById(query.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(query.userId()));
        Account account = accounts.findById(user.getAccountId())
                .orElseThrow(() -> IamExceptions.userNotFound(query.userId()));
        return new UserProfile(user, account.getEmail().value());
    }
}
