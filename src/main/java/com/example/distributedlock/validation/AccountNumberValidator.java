package com.example.distributedlock.validation;

import com.example.distributedlock.models.Account;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 帳戶號碼驗證器
 */
public class AccountNumberValidator implements ConstraintValidator<ValidAccountNumber, String> {
    
    private boolean allowNull;
    
    @Override
    public void initialize(ValidAccountNumber constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
    }
    
    @Override
    public boolean isValid(String accountNumber, ConstraintValidatorContext context) {
        if (accountNumber == null) {
            return allowNull;
        }
        
        return Account.isValidAccountNumber(accountNumber);
    }
}