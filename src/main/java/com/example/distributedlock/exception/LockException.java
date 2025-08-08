package com.example.distributedlock.exception;

/**
 * 分散式鎖異常
 */
public class LockException extends RuntimeException {
    
    public LockException(String message) {
        super(message);
    }
    
    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}