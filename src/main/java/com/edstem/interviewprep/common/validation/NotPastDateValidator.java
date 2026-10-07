package com.edstem.interviewprep.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class NotPastDateValidator implements ConstraintValidator<NotPastDate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        LocalDate today = LocalDate.now(context.getClockProvider().getClock());
        return IsoDateValidator.parse(value)
                .map(date -> !date.isBefore(today))
                .orElse(true);
    }
}
