package com.edstem.interviewprep.common.error;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency-Key %s was already used for a different request".formatted(idempotencyKey));
    }
}
