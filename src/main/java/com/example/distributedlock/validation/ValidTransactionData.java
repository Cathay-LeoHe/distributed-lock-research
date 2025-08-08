package com.example.distributedlock.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定義交易資料完整性驗證註解
 */
@Documented
@Constraint(validatedBy = TransactionDataValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransactionData {
    
    String message() default "交易資料不完整或不正確";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}