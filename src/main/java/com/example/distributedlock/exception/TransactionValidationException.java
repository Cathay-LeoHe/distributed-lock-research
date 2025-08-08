package com.example.distributedlock.exception;

/**
 * 交易驗證異常
 */
public class TransactionValidationException extends ValidationException {
    
    private final String transactionId;
    
    public TransactionValidationException(String message) {
        super(message);
        this.transactionId = null;
    }
    
    public TransactionValidationException(String message, String transactionId) {
        super(message);
        this.transactionId = transactionId;
    }
    
    public TransactionValidationException(String message, String transactionId, Throwable cause) {
        super(message, cause);
        this.transactionId = transactionId;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
}