package com.edstem.interviewprep.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be a real calendar date in ISO {@code yyyy-MM-dd} format. {@code null} is valid.
 */
@Constraint(validatedBy = IsoDateValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoDate {

    String message() default "must be a date in yyyy-MM-dd format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
