package com.example.distributedlock.exception;

import java.math.BigDecimal;

/**
 * 餘額不足異常
 */
public class InsufficientFundsException extends BusinessException {
    
    private final String accountNumber;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;
    
    public InsufficientFundsException(String accountNumber, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("INSUFFICIENT_FUNDS", 
            String.format("帳戶 %s 餘額不足，請求金額: %s，可用餘額: %s", 
                accountNumber, requestedAmount, availableBalance));
        this.accountNumber = accountNumber;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }
    
    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", message);
        this.accountNumber = null;
        this.requestedAmount = null;
        this.availableBalance = null;
    }
    
    public InsufficientFundsException(String message, Throwable cause) {
        super("INSUFFICIENT_FUNDS", message, cause);
        this.accountNumber = null;
        this.requestedAmount = null;
        this.availableBalance = null;
    }
    
    public String getAccountNumber() {
        return accountNumber;
    }
    
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
    
    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}