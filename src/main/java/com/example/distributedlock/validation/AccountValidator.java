package com.example.distributedlock.validation;

import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 帳戶業務邏輯驗證器
 */
@Component
public class AccountValidator {
    
    /**
     * 驗證帳戶是否可以進行扣款操作
     */
    public ValidationResult validateForDebit(Account account, BigDecimal amount) {
        if (account == null) {
            return ValidationResult.failure("帳戶不存在");
        }
        
        if (!account.canPerformTransaction()) {
            return ValidationResult.failure("帳戶狀態不允許交易：" + account.getStatus().getDescription());
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("扣款金額必須大於零");
        }
        
        if (!account.hasSufficientBalance(amount)) {
            return ValidationResult.failure("帳戶餘額不足，當前餘額：" + account.getBalance() + "，需要金額：" + amount);
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證帳戶是否可以進行存款操作
     */
    public ValidationResult validateForCredit(Account account, BigDecimal amount) {
        if (account == null) {
            return ValidationResult.failure("帳戶不存在");
        }
        
        if (account.isClosed()) {
            return ValidationResult.failure("已關閉的帳戶不能進行存款操作");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("存款金額必須大於零");
        }
        
        // 檢查存款後餘額是否會超過限額（假設限額為10,000,000）
        BigDecimal maxBalance = new BigDecimal("10000000.00");
        BigDecimal newBalance = account.getBalance().add(amount);
        if (newBalance.compareTo(maxBalance) > 0) {
            return ValidationResult.failure("存款後餘額將超過帳戶限額：" + maxBalance);
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 驗證帳戶狀態轉換是否有效
     */
    public ValidationResult validateStatusTransition(Account account, AccountStatus newStatus) {
        if (account == null) {
            return ValidationResult.failure("帳戶不存在");
        }
        
        if (newStatus == null) {
            return ValidationResult.failure("新狀態不能為空");
        }
        
        AccountStatus currentStatus = account.getStatus();
        
        // 定義有效的狀態轉換規則
        boolean isValidTransition = switch (currentStatus) {
            case ACTIVE -> newStatus == AccountStatus.FROZEN || 
                          newStatus == AccountStatus.INACTIVE || 
                          newStatus == AccountStatus.CLOSED;
            case INACTIVE -> newStatus == AccountStatus.ACTIVE || 
                            newStatus == AccountStatus.CLOSED;
            case FROZEN -> newStatus == AccountStatus.ACTIVE || 
                          newStatus == AccountStatus.CLOSED;
            case CLOSED -> false; // 已關閉的帳戶不能轉換到其他狀態
        };
        
        if (!isValidTransition) {
            return ValidationResult.failure(
                "無效的狀態轉換：從 " + currentStatus.getDescription() + 
                " 到 " + newStatus.getDescription()
            );
        }
        
        return ValidationResult.success();
    }
}