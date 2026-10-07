package com.edstem.interviewprep.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.validation.FieldError;

public record FieldErrorDetail(String field, String message) {

    static FieldErrorDetail from(FieldError fieldError) {
        return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }

    static FieldErrorDetail from(FieldValidationException exception) {
        return new FieldErrorDetail(exception.getField(), exception.getMessage());
    }

    static FieldErrorDetail from(MismatchedInputException exception) {
        String field = exception.getPath().stream()
                .map(FieldErrorDetail::pathSegment)
                .collect(Collectors.joining("."));
        return forInvalidValue(field, exception.getTargetType());
    }

    static FieldErrorDetail forInvalidValue(String field, Class<?> expectedType) {
        return new FieldErrorDetail(field, field + " " + expectedValueDescription(expectedType));
    }

    private static String pathSegment(JsonMappingException.Reference reference) {
        return reference.getFieldName() != null ? reference.getFieldName() : "[" + reference.getIndex() + "]";
    }

    private static String expectedValueDescription(Class<?> targetType) {
        if (targetType != null && targetType.isEnum()) {
            return "must be one of " + Arrays.toString(targetType.getEnumConstants());
        }
        if (LocalDate.class.equals(targetType)) {
            return "must be a date in yyyy-MM-dd format";
        }
        return "has an invalid value";
    }
}
