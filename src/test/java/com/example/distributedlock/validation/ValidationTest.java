package com.example.distributedlock.validation;

import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.models.Transaction;
import com.example.distributedlock.models.TransactionStatus;
import com.example.distributedlock.models.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 驗證功能測試
 */
public class ValidationTest {
    
    @Test
    public void testAccountValidation() {
        // 測試有效帳戶
        Account validAccount = new Account("ACC001", new BigDecimal("1000.00"));
        ValidationResult result = validAccount.validateAccount();
        assertTrue(result.isValid(), "有效帳戶應該通過驗證");
        
        // 測試無效帳戶號碼
        Account invalidAccount = new Account("", new BigDecimal("1000.00"));
        result = invalidAccount.validateAccount();
        assertFalse(result.isValid(), "空帳戶號碼應該驗證失敗");
        
        // 測試負餘額
        Account negativeBalanceAccount = new Account("ACC002", new BigDecimal("-100.00"));
        assertFalse(negativeBalanceAccount.isBalanceValid(), "負餘額應該驗證失敗");
    }
    
    @Test
    public void testAccountStatusTransition() {
        Account account = new Account("ACC001", new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        
        // 測試有效狀態轉換
        ValidationResult result = account.validateStatusTransition(AccountStatus.FROZEN);
        assertTrue(result.isValid(), "從活躍到凍結應該是有效轉換");
        
        // 測試無效狀態轉換
        account.setStatus(AccountStatus.CLOSED);
        result = account.validateStatusTransition(AccountStatus.ACTIVE);
        assertFalse(result.isValid(), "從關閉到活躍應該是無效轉換");
    }
    
    @Test
    public void testTransactionValidation() {
        // 測試有效轉帳交易
        Transaction validTransfer = new Transaction("ACC001", "ACC002", new BigDecimal("100.00"), TransactionType.TRANSFER);
        ValidationResult result = validTransfer.validateTransaction();
        assertTrue(result.isValid(), "有效轉帳交易應該通過驗證");
        
        // 測試無效轉帳交易（相同帳戶）
        Transaction invalidTransfer = new Transaction("ACC001", "ACC001", new BigDecimal("100.00"), TransactionType.TRANSFER);
        result = invalidTransfer.validateTransaction();
        assertFalse(result.isValid(), "相同帳戶轉帳應該驗證失敗");
        
        // 測試有效扣款交易
        Transaction validWithdrawal = new Transaction("ACC001", null, new BigDecimal("100.00"), TransactionType.WITHDRAWAL);
        result = validWithdrawal.validateTransaction();
        assertTrue(result.isValid(), "有效扣款交易應該通過驗證");
    }
    
    @Test
    public void testTransactionStatusTransition() {
        Transaction transaction = new Transaction("ACC001", "ACC002", new BigDecimal("100.00"), TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PENDING);
        
        // 測試有效狀態轉換
        ValidationResult result = transaction.validateStatusTransition(TransactionStatus.PROCESSING);
        assertTrue(result.isValid(), "從待處理到處理中應該是有效轉換");
        
        // 測試無效狀態轉換
        transaction.setStatus(TransactionStatus.COMPLETED);
        result = transaction.validateStatusTransition(TransactionStatus.PENDING);
        assertFalse(result.isValid(), "從已完成到待處理應該是無效轉換");
    }
    
    @Test
    public void testBusinessLogicMethods() {
        Account account = new Account("ACC001", new BigDecimal("1000.00"));
        
        // 測試餘額檢查
        assertTrue(account.hasSufficientBalance(new BigDecimal("500.00")), "應該有足夠餘額");
        assertFalse(account.hasSufficientBalance(new BigDecimal("1500.00")), "應該餘額不足");
        
        // 測試狀態檢查
        account.setStatus(AccountStatus.ACTIVE);
        assertTrue(account.canPerformTransaction(), "活躍帳戶應該可以交易");
        
        account.setStatus(AccountStatus.FROZEN);
        assertFalse(account.canPerformTransaction(), "凍結帳戶應該不能交易");
        
        // 測試交易狀態轉換邏輯
        Transaction transaction = new Transaction();
        transaction.setStatus(TransactionStatus.PENDING);
        
        assertTrue(transaction.canTransitionTo(TransactionStatus.PROCESSING), "待處理可以轉為處理中");
        assertFalse(transaction.canTransitionTo(TransactionStatus.COMPLETED), "待處理不能直接轉為已完成");
    }
}