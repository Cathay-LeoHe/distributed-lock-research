package com.example.distributedlock.services;

import com.example.distributedlock.dto.AccountBalance;
import com.example.distributedlock.exception.AccountNotFoundException;
import com.example.distributedlock.exception.AccountValidationException;
import com.example.distributedlock.exception.InsufficientFundsException;
import com.example.distributedlock.models.Account;
import com.example.distributedlock.models.AccountStatus;
import com.example.distributedlock.repositories.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    
    @Mock
    private AccountRepository accountRepository;
    
    @InjectMocks
    private AccountService accountService;
    
    private Account testAccount;
    private final String TEST_ACCOUNT_NUMBER = "ACC-001";
    private final BigDecimal TEST_BALANCE = new BigDecimal("1000.00");
    
    @BeforeEach
    void setUp() {
        testAccount = new Account(TEST_ACCOUNT_NUMBER, TEST_BALANCE);
        testAccount.setStatus(AccountStatus.ACTIVE);
        testAccount.setUpdatedAt(LocalDateTime.now());
    }
    
    @Test
    void findByAccountNumber_ShouldReturnAccount_WhenAccountExists() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When
        Account result = accountService.findByAccountNumber(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertNotNull(result);
        assertEquals(TEST_ACCOUNT_NUMBER, result.getAccountNumber());
        assertEquals(TEST_BALANCE, result.getBalance());
        verify(accountRepository).findById(TEST_ACCOUNT_NUMBER);
    }
    
    @Test
    void findByAccountNumber_ShouldThrowException_WhenAccountNotExists() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(AccountNotFoundException.class, 
                () -> accountService.findByAccountNumber(TEST_ACCOUNT_NUMBER));
        verify(accountRepository).findById(TEST_ACCOUNT_NUMBER);
    }
    
    @Test
    void findByAccountNumber_ShouldThrowException_WhenAccountNumberIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                () -> accountService.findByAccountNumber(null));
        verify(accountRepository, never()).findById(anyString());
    }
    
    @Test
    void findByAccountNumber_ShouldThrowException_WhenAccountNumberIsEmpty() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                () -> accountService.findByAccountNumber(""));
        verify(accountRepository, never()).findById(anyString());
    }
    
    @Test
    void findByAccountNumberWithLock_ShouldReturnAccount_WhenAccountExists() {
        // Given
        when(accountRepository.findByAccountNumberWithLock(TEST_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(testAccount));
        
        // When
        Account result = accountService.findByAccountNumberWithLock(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertNotNull(result);
        assertEquals(TEST_ACCOUNT_NUMBER, result.getAccountNumber());
        verify(accountRepository).findByAccountNumberWithLock(TEST_ACCOUNT_NUMBER);
    }
    
    @Test
    void getAccountBalance_ShouldReturnAccountBalance_WhenAccountExists() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When
        AccountBalance result = accountService.getAccountBalance(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertNotNull(result);
        assertEquals(TEST_ACCOUNT_NUMBER, result.getAccountNumber());
        assertEquals(TEST_BALANCE, result.getBalance());
        assertEquals(AccountStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getLastUpdated());
    }
    
    @Test
    void checkSufficientBalance_ShouldReturnTrue_WhenBalanceIsSufficient() {
        // Given
        BigDecimal requestAmount = new BigDecimal("500.00");
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When
        boolean result = accountService.checkSufficientBalance(TEST_ACCOUNT_NUMBER, requestAmount);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void checkSufficientBalance_ShouldThrowException_WhenBalanceIsInsufficient() {
        // Given
        BigDecimal requestAmount = new BigDecimal("1500.00");
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When & Then
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class,
                () -> accountService.checkSufficientBalance(TEST_ACCOUNT_NUMBER, requestAmount));
        
        assertEquals(TEST_ACCOUNT_NUMBER, exception.getAccountNumber());
        assertEquals("1500.00", exception.getRequestedAmount());
        assertEquals("1000.00", exception.getAvailableBalance());
    }
    
    @Test
    void checkSufficientBalance_ShouldThrowException_WhenAmountIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> accountService.checkSufficientBalance(TEST_ACCOUNT_NUMBER, null));
    }
    
    @Test
    void checkSufficientBalance_ShouldThrowException_WhenAmountIsZero() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> accountService.checkSufficientBalance(TEST_ACCOUNT_NUMBER, BigDecimal.ZERO));
    }
    
    @Test
    void canPerformTransaction_ShouldReturnTrue_WhenAccountIsActive() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When
        boolean result = accountService.canPerformTransaction(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    void canPerformTransaction_ShouldThrowException_WhenAccountIsFrozen() {
        // Given
        testAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When & Then
        assertThrows(AccountValidationException.class,
                () -> accountService.canPerformTransaction(TEST_ACCOUNT_NUMBER));
    }
    
    @Test
    void createAccount_ShouldReturnAccount_WhenDataIsValid() {
        // Given
        String newAccountNumber = "ACC-002";
        BigDecimal initialBalance = new BigDecimal("500.00");
        Account newAccount = new Account(newAccountNumber, initialBalance);
        
        when(accountRepository.existsById(newAccountNumber)).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(newAccount);
        
        // When
        Account result = accountService.createAccount(newAccountNumber, initialBalance);
        
        // Then
        assertNotNull(result);
        assertEquals(newAccountNumber, result.getAccountNumber());
        assertEquals(initialBalance, result.getBalance());
        verify(accountRepository).save(any(Account.class));
    }
    
    @Test
    void createAccount_ShouldThrowException_WhenAccountAlreadyExists() {
        // Given
        when(accountRepository.existsById(TEST_ACCOUNT_NUMBER)).thenReturn(true);
        
        // When & Then
        assertThrows(AccountValidationException.class,
                () -> accountService.createAccount(TEST_ACCOUNT_NUMBER, TEST_BALANCE));
        verify(accountRepository, never()).save(any(Account.class));
    }
    
    @Test
    void updateAccountStatus_ShouldReturnUpdatedAccount_WhenTransitionIsValid() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        
        // When
        Account result = accountService.updateAccountStatus(TEST_ACCOUNT_NUMBER, AccountStatus.FROZEN);
        
        // Then
        assertNotNull(result);
        assertEquals(AccountStatus.FROZEN, result.getStatus());
        verify(accountRepository).save(testAccount);
    }
    
    @Test
    void freezeAccount_ShouldFreezeAccount_WhenAccountIsActive() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        
        // When
        Account result = accountService.freezeAccount(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertNotNull(result);
        verify(accountRepository).save(testAccount);
    }
    
    @Test
    void unfreezeAccount_ShouldUnfreezeAccount_WhenAccountIsFrozen() {
        // Given
        testAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        
        // When
        Account result = accountService.unfreezeAccount(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertNotNull(result);
        verify(accountRepository).save(testAccount);
    }
    
    @Test
    void closeAccount_ShouldCloseAccount_WhenBalanceIsZero() {
        // Given
        testAccount.setBalance(BigDecimal.ZERO);
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        
        // When
        Account result = accountService.closeAccount(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertNotNull(result);
        verify(accountRepository).save(testAccount);
    }
    
    @Test
    void closeAccount_ShouldThrowException_WhenBalanceIsNotZero() {
        // Given
        when(accountRepository.findById(TEST_ACCOUNT_NUMBER)).thenReturn(Optional.of(testAccount));
        
        // When & Then
        assertThrows(AccountValidationException.class,
                () -> accountService.closeAccount(TEST_ACCOUNT_NUMBER));
        verify(accountRepository, never()).save(any(Account.class));
    }
    
    @Test
    void findActiveAccounts_ShouldReturnActiveAccounts() {
        // Given
        List<Account> activeAccounts = Arrays.asList(testAccount);
        when(accountRepository.findByStatus(AccountStatus.ACTIVE)).thenReturn(activeAccounts);
        
        // When
        List<Account> result = accountService.findActiveAccounts();
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testAccount, result.get(0));
        verify(accountRepository).findByStatus(AccountStatus.ACTIVE);
    }
    
    @Test
    void findAccountsWithMinBalance_ShouldReturnMatchingAccounts() {
        // Given
        BigDecimal minBalance = new BigDecimal("500.00");
        List<Account> accounts = Arrays.asList(testAccount);
        when(accountRepository.findAccountsWithMinBalance(minBalance)).thenReturn(accounts);
        
        // When
        List<Account> result = accountService.findAccountsWithMinBalance(minBalance);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(accountRepository).findAccountsWithMinBalance(minBalance);
    }
    
    @Test
    void findAccountsWithMinBalance_ShouldThrowException_WhenMinBalanceIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> accountService.findAccountsWithMinBalance(null));
    }
    
    @Test
    void isActiveAccount_ShouldReturnTrue_WhenAccountIsActive() {
        // Given
        when(accountRepository.existsActiveAccount(TEST_ACCOUNT_NUMBER)).thenReturn(true);
        
        // When
        boolean result = accountService.isActiveAccount(TEST_ACCOUNT_NUMBER);
        
        // Then
        assertTrue(result);
        verify(accountRepository).existsActiveAccount(TEST_ACCOUNT_NUMBER);
    }
    
    @Test
    void isActiveAccount_ShouldReturnFalse_WhenAccountNumberIsNull() {
        // When
        boolean result = accountService.isActiveAccount(null);
        
        // Then
        assertFalse(result);
        verify(accountRepository, never()).existsActiveAccount(anyString());
    }
    
    @Test
    void isActiveAccount_ShouldReturnFalse_WhenAccountNumberIsEmpty() {
        // When
        boolean result = accountService.isActiveAccount("");
        
        // Then
        assertFalse(result);
        verify(accountRepository, never()).existsActiveAccount(anyString());
    }
}