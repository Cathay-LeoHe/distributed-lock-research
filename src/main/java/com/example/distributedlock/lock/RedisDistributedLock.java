package com.example.distributedlock.lock;

import com.example.distributedlock.metrics.LockMetrics;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分散式鎖實作
 * 使用 Redisson 客戶端實作分散式鎖功能
 */
@Component
public class RedisDistributedLock implements DistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);
    
    private final RedissonClient redissonClient;
    private final LockMetrics lockMetrics;
    
    // 用於追蹤當前執行緒持有的鎖
    private final ConcurrentHashMap<String, RLock> heldLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockAcquisitionTimes = new ConcurrentHashMap<>();

    public RedisDistributedLock(RedissonClient redissonClient, @Autowired(required = false) LockMetrics lockMetrics) {
        this.redissonClient = redissonClient;
        this.lockMetrics = lockMetrics;
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }

        Instant startTime = Instant.now();
        
        // 記錄鎖獲取嘗試
        if (lockMetrics != null) {
            lockMetrics.recordLockAcquisitionAttempt("redis", lockKey);
        }

        try {
            logger.debug("Attempting to acquire lock: {} with waitTime: {}, leaseTime: {}, unit: {}", 
                        lockKey, waitTime, leaseTime, unit);

            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);

            Duration duration = Duration.between(startTime, Instant.now());

            if (acquired) {
                // 記錄當前執行緒持有的鎖和獲取時間
                heldLocks.put(lockKey, lock);
                lockAcquisitionTimes.put(lockKey, startTime);
                
                // 記錄鎖獲取成功
                if (lockMetrics != null) {
                    lockMetrics.recordLockAcquisitionSuccess("redis", lockKey, duration);
                }
                
                logger.debug("Successfully acquired lock: {} by thread: {} in {}ms", 
                           lockKey, Thread.currentThread().getName(), duration.toMillis());
            } else {
                // 記錄鎖獲取失敗
                if (lockMetrics != null) {
                    lockMetrics.recordLockAcquisitionFailure("redis", lockKey, "timeout", duration);
                }
                
                logger.debug("Failed to acquire lock: {} by thread: {} within {} {} ({}ms)", 
                           lockKey, Thread.currentThread().getName(), waitTime, unit, duration.toMillis());
            }

            return acquired;
            
        } catch (InterruptedException e) {
            Duration duration = Duration.between(startTime, Instant.now());
            
            // 記錄鎖獲取失敗
            if (lockMetrics != null) {
                lockMetrics.recordLockAcquisitionFailure("redis", lockKey, "interrupted", duration);
            }
            
            logger.warn("Lock acquisition interrupted for key: {} by thread: {} after {}ms", 
                       lockKey, Thread.currentThread().getName(), duration.toMillis());
            Thread.currentThread().interrupt(); // 恢復中斷狀態
            throw e;
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            
            // 記錄鎖獲取失敗
            if (lockMetrics != null) {
                lockMetrics.recordLockAcquisitionFailure("redis", lockKey, "exception", duration);
            }
            
            logger.error("Error occurred while trying to acquire lock: {} by thread: {} after {}ms", 
                        lockKey, Thread.currentThread().getName(), duration.toMillis(), e);
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }

        // 記錄鎖釋放嘗試
        if (lockMetrics != null) {
            lockMetrics.recordLockReleaseAttempt("redis", lockKey);
        }

        try {
            RLock lock = heldLocks.get(lockKey);
            Instant acquisitionTime = lockAcquisitionTimes.get(lockKey);
            
            if (lock == null) {
                // 如果本地沒有記錄，嘗試從 Redis 獲取鎖對象
                lock = redissonClient.getLock(lockKey);
            }

            // 檢查鎖是否被當前執行緒持有
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                heldLocks.remove(lockKey);
                lockAcquisitionTimes.remove(lockKey);
                
                // 計算鎖持有時間
                Duration holdDuration = acquisitionTime != null ? 
                    Duration.between(acquisitionTime, Instant.now()) : Duration.ZERO;
                
                // 記錄鎖釋放成功
                if (lockMetrics != null) {
                    lockMetrics.recordLockReleaseSuccess("redis", lockKey, holdDuration);
                }
                
                logger.debug("Successfully released lock: {} by thread: {} after holding for {}ms", 
                           lockKey, Thread.currentThread().getName(), holdDuration.toMillis());
            } else {
                // 記錄鎖釋放失敗
                if (lockMetrics != null) {
                    lockMetrics.recordLockReleaseFailure("redis", lockKey, "not_held_by_current_thread");
                }
                
                logger.warn("Attempted to release lock: {} not held by current thread: {}", 
                          lockKey, Thread.currentThread().getName());
            }
            
        } catch (Exception e) {
            // 記錄鎖釋放失敗
            if (lockMetrics != null) {
                lockMetrics.recordLockReleaseFailure("redis", lockKey, "exception");
            }
            
            logger.error("Error occurred while releasing lock: {} by thread: {}", 
                        lockKey, Thread.currentThread().getName(), e);
            // 即使發生異常，也要清理本地記錄
            heldLocks.remove(lockKey);
            lockAcquisitionTimes.remove(lockKey);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }

        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = lock.isLocked();
            
            logger.debug("Lock status check for key: {} - locked: {}", lockKey, locked);
            return locked;
            
        } catch (Exception e) {
            logger.error("Error occurred while checking lock status for key: {}", lockKey, e);
            return false;
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }

        try {
            RLock lock = heldLocks.get(lockKey);
            
            if (lock == null) {
                // 如果本地沒有記錄，嘗試從 Redis 獲取鎖對象
                lock = redissonClient.getLock(lockKey);
            }

            boolean heldByCurrentThread = lock.isHeldByCurrentThread();
            
            logger.debug("Lock ownership check for key: {} by thread: {} - held: {}", 
                        lockKey, Thread.currentThread().getName(), heldByCurrentThread);
            
            return heldByCurrentThread;
            
        } catch (Exception e) {
            logger.error("Error occurred while checking lock ownership for key: {} by thread: {}", 
                        lockKey, Thread.currentThread().getName(), e);
            return false;
        }
    }

    /**
     * 獲取當前執行緒持有的鎖數量
     * 
     * @return 持有的鎖數量
     */
    public int getHeldLocksCount() {
        return heldLocks.size();
    }

    /**
     * 清理所有本地鎖記錄（通常用於測試或異常情況）
     */
    public void clearHeldLocks() {
        logger.warn("Clearing all held locks for thread: {}", Thread.currentThread().getName());
        heldLocks.clear();
    }

    /**
     * 強制釋放指定鎖（危險操作，僅在特殊情況下使用）
     * 
     * @param lockKey 鎖的唯一標識
     */
    public void forceUnlock(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }

        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isLocked()) {
                lock.forceUnlock();
                heldLocks.remove(lockKey);
                logger.warn("Force unlocked key: {} by thread: {}", 
                          lockKey, Thread.currentThread().getName());
            }
        } catch (Exception e) {
            logger.error("Error occurred while force unlocking key: {}", lockKey, e);
        }
    }
}