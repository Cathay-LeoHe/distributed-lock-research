package com.example.distributedlock.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定義交易狀態驗證註解
 */
@Documented
@Constraint(validatedBy = TransactionStatusValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransactionStatus {
    
    String message() default "交易狀態無效";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}