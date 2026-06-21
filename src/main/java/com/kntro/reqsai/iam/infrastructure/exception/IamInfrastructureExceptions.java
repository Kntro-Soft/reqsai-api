package com.kntro.reqsai.iam.infrastructure.exception;

import com.kntro.reqsai.shared.domain.exception.InfrastructureException;

/**
 * Factory for IAM infrastructure exceptions — the infrastructure counterpart of
 * {@link com.kntro.reqsai.iam.domain.exception.IamExceptions}.
 *
 * <p>Adapters must use this factory instead of constructing {@link InfrastructureException}
 * inline or throwing raw JDK exceptions.
 *
 * @see IamInfrastructureError
 */
public final class IamInfrastructureExceptions {

    private IamInfrastructureExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static InfrastructureException emailDeliveryFailed(String provider, String emailType, Throwable cause) {
        return new InfrastructureException(
                IamInfrastructureError.EMAIL_DELIVERY_FAILED, "[" + provider + "] Failed to send " + emailType, cause);
    }
}
