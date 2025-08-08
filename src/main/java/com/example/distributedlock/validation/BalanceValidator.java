package com.example.distributedlock.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * 餘額驗證器
 */
public class BalanceValidator implements ConstraintValidator<ValidBalance, BigDecimal> {
    
    private BigDecimal minBalance;
    private BigDecimal maxBalance;
    
    @Override
    public void initialize(ValidBalance constraintAnnotation) {
        this.minBalance = new BigDecimal(constraintAnnotation.min());
        this.maxBalance = new BigDecimal(constraintAnnotation.max());
    }
    
    @Override
    public boolean isValid(BigDecimal balance, ConstraintValidatorContext context) {
        if (balance == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "餘額不能為空"
            ).addConstraintViolation();
            return false;
        }
        
        if (balance.compareTo(minBalance) < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "餘額不能小於 " + minBalance
            ).addConstraintViolation();
            return false;
        }
        
        if (balance.compareTo(maxBalance) > 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "餘額不能大於 " + maxBalance
            ).addConstraintViolation();
            return false;
        }
        
        // 檢查小數位數不超過2位
        if (balance.scale() > 2) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "餘額小數位數不能超過2位"
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
}