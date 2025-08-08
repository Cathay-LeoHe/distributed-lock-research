package com.example.distributedlock.dto;

import com.example.distributedlock.models.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易結果 DTO
 */
public class TransactionResult {
    
    private String transactionId;
    private boolean success;
    private String message;
    private TransactionStatus status;
    private BigDecimal amount;
    private String fromAccount;
    private String toAccount;
    private LocalDateTime timestamp;
    private String lockProvider;
    
    // 預設建構子
    public TransactionResult() {
        this.timestamp = LocalDateTime.now();
    }
    
    // 成功結果建構子
    public TransactionResult(String transactionId, BigDecimal amount, String fromAccount, String toAccount) {
        this();
        this.transactionId = transactionId;
        this.success = true;
        this.message = "交易成功";
        this.status = TransactionStatus.COMPLETED;
        this.amount = amount;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
    }
    
    // 失敗結果建構子
    public TransactionResult(String message, TransactionStatus status) {
        this();
        this.success = false;
        this.message = message;
        this.status = status;
    }
    
    // 靜態工廠方法
    public static TransactionResult success(String transactionId, BigDecimal amount, String fromAccount, String toAccount) {
        return new TransactionResult(transactionId, amount, fromAccount, toAccount);
    }
    
    public static TransactionResult failure(String message) {
        return new TransactionResult(message, TransactionStatus.FAILED);
    }
    
    public static TransactionResult failure(String message, TransactionStatus status) {
        return new TransactionResult(message, status);
    }
    
    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getFromAccount() {
        return fromAccount;
    }
    
    public void setFromAccount(String fromAccount) {
        this.fromAccount = fromAccount;
    }
    
    public String getToAccount() {
        return toAccount;
    }
    
    public void setToAccount(String toAccount) {
        this.toAccount = toAccount;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getLockProvider() {
        return lockProvider;
    }
    
    public void setLockProvider(String lockProvider) {
        this.lockProvider = lockProvider;
    }
}