package com.example.distributedlock.models;

/**
 * 交易類型枚舉
 */
public enum TransactionType {
    TRANSFER("匯款"),
    WITHDRAWAL("扣款"),
    DEPOSIT("存款");
    
    private final String description;
    
    TransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}