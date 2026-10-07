package com.edstem.interviewprep.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated ISO date string must be today or later, according to the validator's clock.
 * {@code null} and unparseable values are valid here; combine with {@link IsoDate} to reject bad formats.
 */
@Constraint(validatedBy = NotPastDateValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotPastDate {

    String message() default "must not be in the past";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
