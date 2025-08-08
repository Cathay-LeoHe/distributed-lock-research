package com.example.distributedlock.validation;

/**
 * 驗證結果封裝類別
 */
public class ValidationResult {
    
    private final boolean valid;
    private final String errorMessage;
    
    private ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 創建成功的驗證結果
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    /**
     * 創建失敗的驗證結果
     */
    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
    
    /**
     * 檢查驗證是否成功
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * 檢查驗證是否失敗
     */
    public boolean isInvalid() {
        return !valid;
    }
    
    /**
     * 獲取錯誤訊息
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 如果驗證失敗，拋出異常
     */
    public void throwIfInvalid() {
        if (isInvalid()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
    
    /**
     * 如果驗證失敗，拋出指定類型的異常
     */
    public void throwIfInvalid(Class<? extends RuntimeException> exceptionClass) {
        if (isInvalid()) {
            try {
                throw exceptionClass.getConstructor(String.class).newInstance(errorMessage);
            } catch (Exception e) {
                throw new IllegalArgumentException(errorMessage);
            }
        }
    }
    
    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}