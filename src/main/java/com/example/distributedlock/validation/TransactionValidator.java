package com.example.distributedlock.validation;

import com.example.distributedlock.models.Transaction;
import com.example.distributedlock.models.TransactionStatus;
import com.example.distributedlock.models.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 交易業務邏輯驗證器
 */
@Component
public class TransactionValidator {
    
    /**
     * 驗證交易資料的完整性和正確性
     */
    public ValidationResult validateTransaction(Transaction transaction) {
        if (transaction == null) {
            return ValidationResult.failure("交易資料不能為空");
        }
        
        // 使用實體的內建驗證方法
        if (!transaction.isValid()) {
            return ValidationResult.failure("交易資料不完整或格式不正確");
        }
        
        // 驗證交易金額
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("交易金額必須大於零");
        }
        
        // 根據交易類型進行特定驗證
        return switch (transaction.getType()) {
            case TRANSFER -> validateTransferTransaction(transaction);
            case WITHDRAWAL -> validateWithdrawalTransaction(transaction);
            case DEPOSIT -> validateDepositTransaction(transaction);
        };
    }
    
    /**
     * 驗證轉帳交易
     */
    private ValidationResult validateTransferTransaction(Transaction transaction) {
        if (transaction.getFromAccount() == null || transaction.getFromAccount().trim().isEmpty()) {
            return ValidationResult.failure("轉帳交易必須指定轉出帳戶");
        }
        
        if (transaction.getToAccount() == null || transaction.getToAccount().trim().isEmpty()) {
            return ValidationResult.failure("轉帳交易必須指定轉入帳戶");
        }
        
        if (transaction.getFromAccount().equals(transaction.getToAccount())) {
            return ValidationResult.failure("轉出帳戶和轉入帳戶不能相同");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證扣款交易
     */
    private ValidationResult validateWithdrawalTransaction(Transaction transaction) {
        if (transaction.getFromAccount() == null || transaction.getFromAccount().trim().isEmpty()) {
            return ValidationResult.failure("扣款交易必須指定扣款帳戶");
        }
        
        if (transaction.getToAccount() != null) {
            return ValidationResult.failure("扣款交易不應該指定轉入帳戶");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證存款交易
     */
    private ValidationResult validateDepositTransaction(Transaction transaction) {
        if (transaction.getToAccount() == null || transaction.getToAccount().trim().isEmpty()) {
            return ValidationResult.failure("存款交易必須指定存款帳戶");
        }
        
        if (transaction.getFromAccount() != null) {
            return ValidationResult.failure("存款交易不應該指定轉出帳戶");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證交易狀態轉換
     */
    public ValidationResult validateStatusTransition(Transaction transaction, TransactionStatus newStatus) {
        if (transaction == null) {
            return ValidationResult.failure("交易不存在");
        }
        
        if (newStatus == null) {
            return ValidationResult.failure("新狀態不能為空");
        }
        
        if (!transaction.canTransitionTo(newStatus)) {
            return ValidationResult.failure(
                "無效的狀態轉換：從 " + transaction.getStatus().getDescription() + 
                " 到 " + newStatus.getDescription()
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證交易是否可以取消
     */
    public ValidationResult validateCancellation(Transaction transaction, String reason) {
        if (transaction == null) {
            return ValidationResult.failure("交易不存在");
        }
        
        if (!transaction.isPending()) {
            return ValidationResult.failure("只有待處理狀態的交易才能取消");
        }
        
        if (reason == null || reason.trim().isEmpty()) {
            return ValidationResult.failure("取消交易必須提供原因");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證交易是否可以標記為失敗
     */
    public ValidationResult validateFailure(Transaction transaction, String reason) {
        if (transaction == null) {
            return ValidationResult.failure("交易不存在");
        }
        
        if (transaction.isCompleted() || transaction.isCancelled()) {
            return ValidationResult.failure("已完成或已取消的交易不能標記為失敗");
        }
        
        if (reason == null || reason.trim().isEmpty()) {
            return ValidationResult.failure("交易失敗必須提供原因");
        }
        
        return ValidationResult.success();
    }
}