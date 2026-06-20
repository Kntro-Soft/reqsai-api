package com.kntro.reqsai.iam.application.handler;

import com.kntro.reqsai.iam.application.port.UserRepository;
import com.kntro.reqsai.iam.application.query.GetAuthenticatedUserQuery;
import com.kntro.reqsai.iam.domain.exception.IamExceptions;
import com.kntro.reqsai.iam.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads the authenticated user's profile by id. */
@Component
@RequiredArgsConstructor
public class GetAuthenticatedUserQueryHandler {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public User handle(GetAuthenticatedUserQuery query) {
        return users.findById(query.userId())
                .orElseThrow(() -> IamExceptions.userNotFound(query.userId()));
    }
}
