package com.example.distributedlock.exception;

/**
 * 鎖釋放異常
 * 當無法正確釋放分散式鎖時拋出此異常
 */
public class LockReleaseException extends RuntimeException {

    private final String lockKey;

    public LockReleaseException(String lockKey, String message) {
        super(message);
        this.lockKey = lockKey;
    }

    public LockReleaseException(String lockKey, String message, Throwable cause) {
        super(message, cause);
        this.lockKey = lockKey;
    }

    public String getLockKey() {
        return lockKey;
    }
}