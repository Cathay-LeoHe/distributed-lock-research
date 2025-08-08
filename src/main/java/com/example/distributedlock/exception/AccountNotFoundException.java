package com.example.distributedlock.exception;

/**
 * 帳戶不存在異常
 */
public class AccountNotFoundException extends BusinessException {
    
    public AccountNotFoundException(String accountNumber) {
        super("ACCOUNT_NOT_FOUND", "帳戶不存在: " + accountNumber);
    }
    
    public AccountNotFoundException(String accountNumber, Throwable cause) {
        super("ACCOUNT_NOT_FOUND", "帳戶不存在: " + accountNumber, cause);
    }
}