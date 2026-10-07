package com.edstem.interviewprep.common.error;

public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String resourceName, String identifierName, Object identifier) {
        super("%s with %s %s already exists".formatted(resourceName, identifierName, identifier));
    }
}
