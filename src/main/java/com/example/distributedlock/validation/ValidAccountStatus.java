package com.example.distributedlock.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定義帳戶狀態驗證註解
 */
@Documented
@Constraint(validatedBy = AccountStatusValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAccountStatus {
    
    String message() default "帳戶狀態無效";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}