package com.edstem.interviewprep.common.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("%s with id %s was not found".formatted(resourceName, id));
    }
}
