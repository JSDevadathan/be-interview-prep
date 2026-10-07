package com.edstem.interviewprep.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class IsoDateValidator implements ConstraintValidator<IsoDate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || parse(value).isPresent();
    }

    static Optional<LocalDate> parse(String value) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException invalidDate) {
            return Optional.empty();
        }
    }
}
