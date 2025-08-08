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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceSimpleTest {
    
    @Mock
    private AccountService accountService;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private DistributedLockFactory lockFactory;
    
    @Mock
    private DistributedLock distributedLock;
    
    @InjectMocks
    private TransferService transferService;
    
    private Account fromAccount;
    private Account toAccount;
    private Transaction testTransaction;
    
    private final String FROM_ACCOUNT_NUMBER = "ACC-001";
    private final String TO_ACCOUNT_NUMBER = "ACC-002";
    private final BigDecimal TRANSFER_AMOUNT = new BigDecimal("500.00");
    private final BigDecimal FROM_BALANCE = new BigDecimal("1000.00");
    private final BigDecimal TO_BALANCE = new BigDecimal("500.00");
    
    @BeforeEach
    void setUp() {
        fromAccount = new Account(FROM_ACCOUNT_NUMBER, FROM_BALANCE);
        fromAccount.setStatus(AccountStatus.ACTIVE);
        
        toAccount = new Account(TO_ACCOUNT_NUMBER, TO_BALANCE);
        toAccount.setStatus(AccountStatus.ACTIVE);
        
        testTransaction = new Transaction(FROM_ACCOUNT_NUMBER, TO_ACCOUNT_NUMBER, TRANSFER_AMOUNT, TransactionType.TRANSFER);
        testTransaction.setTransactionId("TXN-001");
    }
    
    @Test
    void transfer_ShouldReturnFailure_WhenFromAccountIsNull() {
        // When
        TransactionResult result = transferService.transfer(null, TO_ACCOUNT_NUMBER, TRANSFER_AMOUNT);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("轉出帳戶號碼不能為空", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldReturnFailure_WhenToAccountIsNull() {
        // When
        TransactionResult result = transferService.transfer(FROM_ACCOUNT_NUMBER, null, TRANSFER_AMOUNT);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("轉入帳戶號碼不能為空", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldReturnFailure_WhenFromAndToAccountAreSame() {
        // When
        TransactionResult result = transferService.transfer(FROM_ACCOUNT_NUMBER, FROM_ACCOUNT_NUMBER, TRANSFER_AMOUNT);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("轉出帳戶和轉入帳戶不能相同", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldReturnFailure_WhenAmountIsZero() {
        // When
        TransactionResult result = transferService.transfer(FROM_ACCOUNT_NUMBER, TO_ACCOUNT_NUMBER, BigDecimal.ZERO);
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("匯款金額必須大於零", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void transfer_ShouldReturnFailure_WhenAmountIsNegative() {
        // When
        TransactionResult result = transferService.transfer(FROM_ACCOUNT_NUMBER, TO_ACCOUNT_NUMBER, new BigDecimal("-100"));
        
        // Then
        assertFalse(result.isSuccess());
        assertEquals("匯款金額必須大於零", result.getMessage());
        
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
    
    @Test
    void validateTransferPreconditions_ShouldReturnNull_WhenAllConditionsAreMet() {
        // Given
        when(accountService.isActiveAccount(FROM_ACCOUNT_NUMBER)).thenReturn(true);
        when(accountService.isActiveAccount(TO_ACCOUNT_NUMBER)).thenReturn(true);
        when(accountService.checkSufficientBalance(FROM_ACCOUNT_NUMBER, TRANSFER_AMOUNT)).thenReturn(true);
        
        // When
        String result = transferService.validateTransferPreconditions(FROM_ACCOUNT_NUMBER, TO_ACCOUNT_NUMBER, TRANSFER_AMOUNT);
        
        // Then
        assertNull(result);
    }
    
    @Test
    void validateTransferPreconditions_ShouldReturnError_WhenFromAccountIsEmpty() {
        // When
        String result = transferService.validateTransferPreconditions("", TO_ACCOUNT_NUMBER, TRANSFER_AMOUNT);
        
        // Then
        assertEquals("轉出帳戶號碼不能為空", result);
    }
    
    @Test
    void validateTransferPreconditions_ShouldReturnError_WhenAccountsAreSame() {
        // When
        String result = transferService.validateTransferPreconditions(FROM_ACCOUNT_NUMBER, FROM_ACCOUNT_NUMBER, TRANSFER_AMOUNT);
        
        // Then
        assertEquals("轉出帳戶和轉入帳戶不能相同", result);
    }
    
    @Test
    void validateTransferPreconditions_ShouldReturnError_WhenAmountIsZero() {
        // When
        String result = transferService.validateTransferPreconditions(FROM_ACCOUNT_NUMBER, TO_ACCOUNT_NUMBER, BigDecimal.ZERO);
        
        // Then
        assertEquals("匯款金額必須大於零", result);
    }
    
    @Test
    void checkLockHealth_ShouldReturnHealthyStatus_WhenLockWorksCorrectly() throws InterruptedException {
        // Given
        when(lockFactory.getDistributedLock()).thenReturn(distributedLock);
        when(lockFactory.getCurrentProvider()).thenReturn("redis");
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // When
        String result = transferService.checkLockHealth();
        
        // Then
        assertTrue(result.contains("分散式鎖健康狀態良好"));
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
        String result = transferService.checkLockHealth();
        
        // Then
        assertEquals("分散式鎖健康檢查失敗：無法獲取測試鎖", result);
        
        verify(distributedLock).tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class));
        verify(distributedLock, never()).unlock(anyString());
    }
}