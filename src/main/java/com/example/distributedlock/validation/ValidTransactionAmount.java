package com.example.distributedlock.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定義交易金額驗證註解
 */
@Documented
@Constraint(validatedBy = TransactionAmountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransactionAmount {
    
    String message() default "交易金額必須大於零且不超過限額";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 最小金額（預設0.01）
     */
    String min() default "0.01";
    
    /**
     * 最大金額（預設1000000.00）
     */
    String max() default "1000000.00";
}