package com.example.distributedlock.exception;

/**
 * 鎖獲取異常
 * 當無法獲取分散式鎖時拋出此異常
 */
public class LockAcquisitionException extends RuntimeException {

    private final String lockKey;

    public LockAcquisitionException(String lockKey, String message) {
        super(message);
        this.lockKey = lockKey;
    }

    public LockAcquisitionException(String lockKey, String message, Throwable cause) {
        super(message, cause);
        this.lockKey = lockKey;
    }

    public String getLockKey() {
        return lockKey;
    }
}