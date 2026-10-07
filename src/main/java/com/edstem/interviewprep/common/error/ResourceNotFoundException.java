package com.edstem.interviewprep.common.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        this(resourceName, "id", id);
    }

    public ResourceNotFoundException(String resourceName, String identifierName, Object identifier) {
        super("%s with %s %s was not found".formatted(resourceName, identifierName, identifier));
    }
}
