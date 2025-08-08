package com.example.distributedlock.exception;

/**
 * 鎖獲取超時異常
 * 當在指定時間內無法獲取鎖時拋出此異常
 */
public class LockTimeoutException extends LockAcquisitionException {

    private final long waitTime;
    private final String timeUnit;

    public LockTimeoutException(String lockKey, long waitTime, String timeUnit) {
        super(lockKey, String.format("Failed to acquire lock '%s' within %d %s", lockKey, waitTime, timeUnit));
        this.waitTime = waitTime;
        this.timeUnit = timeUnit;
    }

    public LockTimeoutException(String lockKey, long waitTime, String timeUnit, Throwable cause) {
        super(lockKey, String.format("Failed to acquire lock '%s' within %d %s", lockKey, waitTime, timeUnit), cause);
        this.waitTime = waitTime;
        this.timeUnit = timeUnit;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public String getTimeUnit() {
        return timeUnit;
    }
}