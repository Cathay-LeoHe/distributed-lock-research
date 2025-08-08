package com.example.distributedlock.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * 交易金額驗證器
 */
public class TransactionAmountValidator implements ConstraintValidator<ValidTransactionAmount, BigDecimal> {
    
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    
    @Override
    public void initialize(ValidTransactionAmount constraintAnnotation) {
        this.minAmount = new BigDecimal(constraintAnnotation.min());
        this.maxAmount = new BigDecimal(constraintAnnotation.max());
    }
    
    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        if (amount == null) {
            return false;
        }
        
        if (amount.compareTo(minAmount) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "交易金額不能小於 " + minAmount
            ).addConstraintViolation();
            return false;
        }
        
        if (amount.compareTo(maxAmount) > 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "交易金額不能大於 " + maxAmount
            ).addConstraintViolation();
            return false;
        }
        
        // 檢查小數位數不超過2位
        if (amount.scale() > 2) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "交易金額小數位數不能超過2位"
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
}