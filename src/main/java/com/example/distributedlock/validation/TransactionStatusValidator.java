package com.example.distributedlock.validation;

import com.example.distributedlock.models.TransactionStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 交易狀態驗證器
 */
public class TransactionStatusValidator implements ConstraintValidator<ValidTransactionStatus, TransactionStatus> {
    
    @Override
    public void initialize(ValidTransactionStatus constraintAnnotation) {
        // 初始化邏輯（如果需要）
    }
    
    @Override
    public boolean isValid(TransactionStatus status, ConstraintValidatorContext context) {
        // TransactionStatus 是枚舉類型，如果不為null則必定是有效值
        return status != null;
    }
}