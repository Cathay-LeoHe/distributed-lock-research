package com.example.distributedlock.services;

import com.example.distributedlock.dto.AccountBalance;
import com.example.distributedlock.dto.TransactionResult;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.models.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * BankingServiceImpl 測試
 */
@ExtendWith(MockitoExtension.class)
class BankingServiceImplTest {
    
    @Mock
    private AccountService accountService;
    
    @Mock
    private TransferService transferService;
    
    @Mock
    private WithdrawalService withdrawalService;
    
    private BankingServiceImpl bankingService;
    
    @BeforeEach
    void setUp() {
        bankingService = new BankingServiceImpl(accountService, transferService, withdrawalService);
    }
    
    @Test
    void testTransferSuccess() {
        // 準備測試資料
        Account fromAccount = createAccount("ACC001", new BigDecimal("1000.00"));
        Account toAccount = createAccount("ACC002", new BigDecimal("500.00"));
        TransactionResult expectedResult = TransactionResult.success("TXN001", 
            new BigDecimal("100.00"), "ACC001", "ACC002");
        
        when(accountService.findByAccountNumber("ACC001")).thenReturn(fromAccount);
        when(accountService.findByAccountNumber("ACC002")).thenReturn(toAccount);
        when(transferService.transfer(anyString(), anyString(), any(BigDecimal.class)))
            .thenReturn(expectedResult);
        
        // 執行測試
        TransactionResult result = bankingService.transfer("ACC001", "ACC002", new BigDecimal("100.00"));
        
        // 驗證結果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("TXN001", result.getTransactionId());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("ACC001", result.getFromAccount());
        assertEquals("ACC002", result.getToAccount());
    }
    
    @Test
    void testTransferFromAccountNotFound() {
        // 準備測試資料
        when(accountService.findByAccountNumber("ACC001")).thenReturn(null);
        
        // 執行測試
        TransactionResult result = bankingService.transfer("ACC001", "ACC002", new BigDecimal("100.00"));
        
        // 驗證結果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("轉出帳戶不存在"));
    }
    
    @Test
    void testTransferToAccountNotFound() {
        // 準備測試資料
        Account fromAccount = createAccount("ACC001", new BigDecimal("1000.00"));
        
        when(accountService.findByAccountNumber("ACC001")).thenReturn(fromAccount);
        when(accountService.findByAccountNumber("ACC002")).thenReturn(null);
        
        // 執行測試
        TransactionResult result = bankingService.transfer("ACC001", "ACC002", new BigDecimal("100.00"));
        
        // 驗證結果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("轉入帳戶不存在"));
    }
    
    @Test
    void testTransferSameAccount() {
        // 執行測試
        TransactionResult result = bankingService.transfer("ACC001", "ACC001", new BigDecimal("100.00"));
        
        // 驗證結果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("轉出帳戶和轉入帳戶不能相同"));
    }
    
    @Test
    void testTransferInvalidAmount() {
        // 執行測試
        TransactionResult result = bankingService.transfer("ACC001", "ACC002", BigDecimal.ZERO);
        
        // 驗證結果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("匯款金額必須大於0"));
    }
    
    @Test
    void testWithdrawSuccess() {
        // 準備測試資料
        Account account = createAccount("ACC001", new BigDecimal("1000.00"));
        TransactionResult expectedResult = TransactionResult.success("TXN002", 
            new BigDecimal("100.00"), "ACC001", null);
        
        when(accountService.findByAccountNumber("ACC001")).thenReturn(account);
        when(withdrawalService.withdraw(anyString(), any(BigDecimal.class)))
            .thenReturn(expectedResult);
        
        // 執行測試
        TransactionResult result = bankingService.withdraw("ACC001", new BigDecimal("100.00"));
        
        // 驗證結果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("TXN002", result.getTransactionId());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("ACC001", result.getFromAccount());
    }
    
    @Test
    void testWithdrawAccountNotFound() {
        // 準備測試資料
        when(accountService.findByAccountNumber("ACC001")).thenReturn(null);
        
        // 執行測試
        TransactionResult result = bankingService.withdraw("ACC001", new BigDecimal("100.00"));
        
        // 驗證結果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("帳戶不存在"));
    }
    
    @Test
    void testWithdrawInvalidAmount() {
        // 執行測試
        TransactionResult result = bankingService.withdraw("ACC001", BigDecimal.ZERO);
        
        // 驗證結果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("扣款金額必須大於0"));
    }
    
    @Test
    void testGetBalanceSuccess() {
        // 準備測試資料
        Account account = createAccount("ACC001", new BigDecimal("1000.00"));
        
        when(accountService.findByAccountNumber("ACC001")).thenReturn(account);
        
        // 執行測試
        AccountBalance result = bankingService.getBalance("ACC001");
        
        // 驗證結果
        assertNotNull(result);
        assertEquals("ACC001", result.getAccountNumber());
        assertEquals(new BigDecimal("1000.00"), result.getBalance());
        assertEquals(AccountStatus.ACTIVE, result.getStatus());
    }
    
    @Test
    void testGetBalanceAccountNotFound() {
        // 準備測試資料
        when(accountService.findByAccountNumber("ACC001")).thenReturn(null);
        
        // 執行測試
        AccountBalance result = bankingService.getBalance("ACC001");
        
        // 驗證結果
        assertNull(result);
    }
    
    @Test
    void testGetBalanceEmptyAccount() {
        // 執行測試
        AccountBalance result = bankingService.getBalance("");
        
        // 驗證結果
        assertNull(result);
    }
    
    private Account createAccount(String accountNumber, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        account.setVersion(1L);
        return account;
    }
}