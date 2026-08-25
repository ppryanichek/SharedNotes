package com.eugene.sharednotes.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}")
public @interface ValidPassword {

    String message() default "Password must be at least 8 characters and include lowercase, uppercase, and digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
