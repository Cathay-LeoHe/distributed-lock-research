package com.example.distributedlock.models;

/**
 * 帳戶狀態枚舉
 */
public enum AccountStatus {
    ACTIVE("活躍"),
    INACTIVE("非活躍"),
    FROZEN("凍結"),
    CLOSED("關閉");
    
    private final String description;
    
    AccountStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}