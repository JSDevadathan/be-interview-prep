package com.edstem.interviewprep.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be the name of one of the constants of {@link #enumClass()}.
 * The message can use {@code {allowedValues}} to list the accepted names. {@code null} is valid.
 */
@Constraint(validatedBy = EnumValueValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {

    Class<? extends Enum<?>> enumClass();

    String message() default "must be one of {allowedValues}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
