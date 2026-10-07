package com.edstem.interviewprep.common.error;

public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String resourceName, String identifierName, Object identifier) {
        this("%s with %s %s already exists".formatted(resourceName, identifierName, identifier));
    }
}
