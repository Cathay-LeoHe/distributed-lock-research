package com.example.distributedlock.dto;

import com.example.distributedlock.models.AccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帳戶餘額 DTO
 */
public class AccountBalance {
    
    private String accountNumber;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime lastUpdated;
    
    // 預設建構子
    public AccountBalance() {
    }
    
    // 建構子
    public AccountBalance(String accountNumber, BigDecimal balance, AccountStatus status, LocalDateTime lastUpdated) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = status;
        this.lastUpdated = lastUpdated;
    }
    
    // Getters and Setters
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
    
    public BigDecimal getBalance() {
        return balance;
    }
    
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}