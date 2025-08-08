package com.example.distributedlock.exception;

/**
 * 帳戶驗證異常
 */
public class AccountValidationException extends ValidationException {
    
    private final String accountNumber;
    
    public AccountValidationException(String message, String accountNumber) {
        super(message);
        this.accountNumber = accountNumber;
    }
    
    public AccountValidationException(String message, String accountNumber, Throwable cause) {
        super(message, cause);
        this.accountNumber = accountNumber;
    }
    
    public String getAccountNumber() {
        return accountNumber;
    }
}