package com.edstem.interviewprep.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be an absolute {@code http} or {@code https} URL with a host. {@code null} is valid.
 */
@Constraint(validatedBy = HttpUrlValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    String message() default "must be an absolute http or https URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
