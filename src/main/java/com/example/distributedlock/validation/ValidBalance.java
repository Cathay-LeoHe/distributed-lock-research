package com.example.distributedlock.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定義餘額驗證註解
 */
@Documented
@Constraint(validatedBy = BalanceValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBalance {
    
    String message() default "餘額必須為非負數且不超過限額";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 最小餘額（預設0.00）
     */
    String min() default "0.00";
    
    /**
     * 最大餘額（預設10000000.00）
     */
    String max() default "10000000.00";
}