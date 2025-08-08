package com.example.distributedlock.services;

import com.example.distributedlock.dto.TransactionResult;
import com.example.distributedlock.factory.DistributedLockFactory;
import com.example.distributedlock.lock.DistributedLock;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.models.Transaction;
import com.example.distributedlock.models.TransactionType;
import com.example.distributedlock.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    
    @Mock
    private AccountService accountService;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private DistributedLockFactory lockFactory;
    
    @Mock
    private DistributedLock distributedLock;
    
    @InjectMocks
    private WithdrawalService withdrawalService;
    
    private Account testAccount;
    private Transaction testTransaction;
    
    private final String ACCOUNT_NUMBER = "ACC-001";
    private final BigDecimal WITHDRAWAL_AMOUNT = new BigDecimal("500.00");
    private final BigDecimal ACCOUNT_BALANCE = new BigDecimal("1000.00");
    
    @BeforeEach
    void setUp() {
        testAccount = new Account(ACCOUNT_NUMBER, ACCOUNT_BALANCE);
        testAccount.setStatus(AccountStatus.ACTIVE);
        
        testTransaction = new Transaction(ACCOUNT_NUMBER, null, WITHDRAWAL_AMOUNT, TransactionType.WITHDRAWAL);
        testTransaction.setTransactionId("TXN-001");
    }
    
    @Test
    void withdraw_ShouldReturnFailure_WhenAccountNumberIsNull() {
        // When
        TransactionResult result = withdrawalService.withdraw(null, WITHDRAWAL_AMOUNT);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("扣款帳戶號碼不能為空", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void withdraw_ShouldReturnFailure_WhenAccountNumberIsEmpty() {
        // When
        TransactionResult result = withdrawalService.withdraw("", WITHDRAWAL_AMOUNT);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("扣款帳戶號碼不能為空", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void withdraw_ShouldReturnFailure_WhenAmountIsNull() {
        // When
        TransactionResult result = withdrawalService.withdraw(ACCOUNT_NUMBER, null);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("扣款金額必須大於零", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void withdraw_ShouldReturnFailure_WhenAmountIsZero() {
        // When
        TransactionResult result = withdrawalService.withdraw(ACCOUNT_NUMBER, BigDecimal.ZERO);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("扣款金額必須大於零", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void withdraw_ShouldReturnFailure_WhenAmountIsNegative() {
        // When
        TransactionResult result = withdrawalService.withdraw(ACCOUNT_NUMBER, new BigDecimal("-100"));
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("扣款金額必須大於零", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void validateWithdrawalPreconditions_ShouldReturnNull_WhenAllConditionsAreMet() {
        // Given
        when(accountService.isActiveAccount(ACCOUNT_NUMBER)).thenReturn(true);
        when(accountService.checkSufficientBalance(ACCOUNT_NUMBER, WITHDRAWAL_AMOUNT)).thenReturn(true);
        
        // When
        String result = withdrawalService.validateWithdrawalPreconditions(ACCOUNT_NUMBER, WITHDRAWAL_AMOUNT);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void validateWithdrawalPreconditions_ShouldReturnError_WhenAccountNumberIsEmpty() {
        // When
        String result = withdrawalService.validateWithdrawalPreconditions("", WITHDRAWAL_AMOUNT);
        
        // Then
        assertEquals("扣款帳戶號碼不能為空", result);
    }
    
    @Test
    void validateWithdrawalPreconditions_ShouldReturnError_WhenAmountIsZero() {
        // When
        String result = withdrawalService.validateWithdrawalPreconditions(ACCOUNT_NUMBER, BigDecimal.ZERO);
        
        // Then
        assertEquals("扣款金額必須大於零", result);
    }
    
    @Test
    void validateWithdrawalPreconditions_ShouldReturnError_WhenAccountNotActive() {
        // Given
        when(accountService.isActiveAccount(ACCOUNT_NUMBER)).thenReturn(false);
        
        // When
        String result = withdrawalService.validateWithdrawalPreconditions(ACCOUNT_NUMBER, WITHDRAWAL_AMOUNT);
        
        // Then
        assertEquals("扣款帳戶不存在或不是活躍狀態", result);
    }
    
    @Test
    void getWithdrawalHistory_ShouldReturnWithdrawalTransactions() {
        // Given
        Transaction withdrawal1 = new Transaction(ACCOUNT_NUMBER, null, WITHDRAWAL_AMOUNT, TransactionType.WITHDRAWAL);
        Transaction withdrawal2 = new Transaction(ACCOUNT_NUMBER, null, WITHDRAWAL_AMOUNT, TransactionType.WITHDRAWAL);
        Transaction transfer = new Transaction(ACCOUNT_NUMBER, "ACC-002", WITHDRAWAL_AMOUNT, TransactionType.TRANSFER);
        
        List<Transaction> allTransactions = Arrays.asList(withdrawal1, withdrawal2, transfer);
        when(transactionRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(allTransactions);
        
        // When
        List<Transaction> result = withdrawalService.getWithdrawalHistory(ACCOUNT_NUMBER);
        
        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(Transaction::isWithdrawal));
    }
    
    @Test
    void getWithdrawalHistory_ShouldThrowException_WhenAccountNumberIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                () -> withdrawalService.getWithdrawalHistory(null));
    }
    
    @Test
    void canWithdraw_ShouldReturnTrue_WhenAllConditionsAreMet() {
        // Given
        when(accountService.isActiveAccount(ACCOUNT_NUMBER)).thenReturn(true);
        when(accountService.checkSufficientBalance(ACCOUNT_NUMBER, WITHDRAWAL_AMOUNT)).thenReturn(true);
        
        // When
        boolean result = withdrawalService.canWithdraw(ACCOUNT_NUMBER, WITHDRAWAL_AMOUNT);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void canWithdraw_ShouldReturnFalse_WhenAccountNotActive() {
        // Given
        when(accountService.isActiveAccount(ACCOUNT_NUMBER)).thenReturn(false);
        
        // When
        boolean result = withdrawalService.canWithdraw(ACCOUNT_NUMBER, WITHDRAWAL_AMOUNT);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    void checkLockHealth_ShouldReturnHealthyStatus_WhenLockWorksCorrectly() throws InterruptedException {
        // Given
        when(lockFactory.getDistributedLock()).thenReturn(distributedLock);
        when(lockFactory.getCurrentProvider()).thenReturn("redis");
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // When
        String result = withdrawalService.checkLockHealth();
        
        // Then
        assertTrue(result.contains("扣款服務分散式鎖健康狀態良好"));
        assertTrue(result.contains("redis"));
        
        verify(distributedLock).tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class));
        verify(distributedLock).unlock(anyString());
    }
    
    @Test
    void checkLockHealth_ShouldReturnUnhealthyStatus_WhenCannotAcquireLock() throws InterruptedException {
        // Given
        when(lockFactory.getDistributedLock()).thenReturn(distributedLock);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // When
        String result = withdrawalService.checkLockHealth();
        
        // Then
        assertEquals("扣款服務分散式鎖健康檢查失敗：無法獲取測試鎖", result);
        
        verify(distributedLock).tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class));
        verify(distributedLock, never()).unlock(anyString());
    }
    
    @Test
    void batchWithdraw_ShouldReturnError_WhenRequestListIsEmpty() {
        // When
        WithdrawalService.BatchWithdrawalResult result = withdrawalService.batchWithdraw(Arrays.asList());
        
        // Then
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals("批量扣款請求列表不能為空", result.getErrorMessages());
    }
    
    @Test
    void batchWithdraw_ShouldReturnError_WhenRequestListIsNull() {
        // When
        WithdrawalService.BatchWithdrawalResult result = withdrawalService.batchWithdraw(null);
        
        // Then
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals("批量扣款請求列表不能為空", result.getErrorMessages());
    }
}