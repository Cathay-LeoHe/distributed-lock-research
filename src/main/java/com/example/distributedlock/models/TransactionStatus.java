package com.example.distributedlock.models;

/**
 * 交易狀態枚舉
 */
public enum TransactionStatus {
    PENDING("待處理"),
    PROCESSING("處理中"),
    COMPLETED("已完成"),
    FAILED("失敗"),
    CANCELLED("已取消");
    
    private final String description;
    
    TransactionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}