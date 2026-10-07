package com.edstem.interviewprep.common.error;

import org.springframework.validation.FieldError;

public record FieldErrorDetail(String field, String message) {

    static FieldErrorDetail from(FieldError fieldError) {
        return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
