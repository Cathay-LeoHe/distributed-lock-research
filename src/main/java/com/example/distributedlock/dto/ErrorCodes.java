package com.example.distributedlock.dto;

/**
 * 錯誤碼定義
 * 統一定義系統中使用的錯誤碼
 */
public final class ErrorCodes {
    
    // 驗證錯誤
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String CONSTRAINT_VIOLATION = "CONSTRAINT_VIOLATION";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    
    // 業務錯誤
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String INVALID_TRANSACTION = "INVALID_TRANSACTION";
    
    // 系統錯誤
    public static final String LOCK_ERROR = "LOCK_ERROR";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    
    // HTTP 狀態碼對應
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;
    public static final int SERVICE_UNAVAILABLE = 503;
    
    // 私有建構子，防止實例化
    private ErrorCodes() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}