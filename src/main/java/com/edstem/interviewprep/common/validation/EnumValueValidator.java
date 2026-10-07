package com.edstem.interviewprep.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private static final String ALLOWED_VALUES_PARAMETER = "allowedValues";

    private Set<String> allowedNames;
    private String allowedValues;

    @Override
    public void initialize(EnumValue annotation) {
        Enum<?>[] constants = annotation.enumClass().getEnumConstants();
        allowedNames = Arrays.stream(constants).map(Enum::name).collect(Collectors.toUnmodifiableSet());
        allowedValues = Arrays.toString(constants);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || allowedNames.contains(value)) {
            return true;
        }
        context.unwrap(HibernateConstraintValidatorContext.class)
                .addMessageParameter(ALLOWED_VALUES_PARAMETER, allowedValues);
        return false;
    }
}
