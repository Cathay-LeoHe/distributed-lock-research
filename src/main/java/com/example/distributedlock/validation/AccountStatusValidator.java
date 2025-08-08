package com.example.distributedlock.validation;

import com.example.distributedlock.models.AccountStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 帳戶狀態驗證器
 */
public class AccountStatusValidator implements ConstraintValidator<ValidAccountStatus, AccountStatus> {
    
    @Override
    public void initialize(ValidAccountStatus constraintAnnotation) {
        // 初始化邏輯（如果需要）
    }
    
    @Override
    public boolean isValid(AccountStatus status, ConstraintValidatorContext context) {
        // AccountStatus 是枚舉類型，如果不為null則必定是有效值
        return status != null;
    }
}