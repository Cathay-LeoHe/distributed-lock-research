package com.example.distributedlock.validation;

import com.example.distributedlock.models.Transaction;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 交易資料完整性驗證器
 */
public class TransactionDataValidator implements ConstraintValidator<ValidTransactionData, Transaction> {
    
    @Override
    public void initialize(ValidTransactionData constraintAnnotation) {
        // 初始化邏輯（如果需要）
    }
    
    @Override
    public boolean isValid(Transaction transaction, ConstraintValidatorContext context) {
        if (transaction == null) {
            return false;
        }
        
        boolean isValid = true;
        context.disableDefaultConstraintViolation();
        
        // 使用Transaction實體的isValid方法進行基本驗證
        if (!transaction.isValid()) {
            context.buildConstraintViolationWithTemplate(
                "交易資料不完整：請檢查帳戶資訊和交易類型的匹配性"
            ).addConstraintViolation();
            isValid = false;
        }
        
        // 額外的業務邏輯驗證
        if (transaction.getFromAccount() != null && transaction.getToAccount() != null) {
            if (transaction.getFromAccount().equals(transaction.getToAccount())) {
                context.buildConstraintViolationWithTemplate(
                    "轉出帳戶和轉入帳戶不能相同"
                ).addConstraintViolation();
                isValid = false;
            }
        }
        
        return isValid;
    }
}