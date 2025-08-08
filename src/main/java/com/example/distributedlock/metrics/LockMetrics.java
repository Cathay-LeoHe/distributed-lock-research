package com.example.distributedlock.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 分散式鎖指標收集器
 * 收集鎖獲取、釋放和性能相關的指標
 */
@Component
public class LockMetrics {

    private static final Logger logger = LoggerFactory.getLogger(LockMetrics.class);

    private final MeterRegistry meterRegistry;
    
    // 計數器
    private final Counter lockAcquisitionAttempts;
    private final Counter lockAcquisitionSuccess;
    private final Counter lockAcquisitionFailures;
    private final Counter lockReleaseAttempts;
    private final Counter lockReleaseSuccess;
    private final Counter lockReleaseFailures;
    private final Counter lockTimeouts;
    
    // 計時器
    private final Timer lockAcquisitionTimer;
    private final Timer lockHoldTimer;
    
    // 原子計數器用於 Gauge
    private final AtomicLong activeLocks = new AtomicLong(0);
    private final AtomicLong totalLocksAcquired = new AtomicLong(0);
    private final AtomicLong totalLocksReleased = new AtomicLong(0);
    
    // 按鎖類型分組的指標
    private final Map<String, Counter> lockTypeCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> lockTypeTimers = new ConcurrentHashMap<>();

    public LockMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化計數器
        this.lockAcquisitionAttempts = Counter.builder("distributed.lock.acquisition.attempts")
            .description("分散式鎖獲取嘗試次數")
            .register(meterRegistry);
            
        this.lockAcquisitionSuccess = Counter.builder("distributed.lock.acquisition.success")
            .description("分散式鎖獲取成功次數")
            .register(meterRegistry);
            
        this.lockAcquisitionFailures = Counter.builder("distributed.lock.acquisition.failures")
            .description("分散式鎖獲取失敗次數")
            .register(meterRegistry);
            
        this.lockReleaseAttempts = Counter.builder("distributed.lock.release.attempts")
            .description("分散式鎖釋放嘗試次數")
            .register(meterRegistry);
            
        this.lockReleaseSuccess = Counter.builder("distributed.lock.release.success")
            .description("分散式鎖釋放成功次數")
            .register(meterRegistry);
            
        this.lockReleaseFailures = Counter.builder("distributed.lock.release.failures")
            .description("分散式鎖釋放失敗次數")
            .register(meterRegistry);
            
        this.lockTimeouts = Counter.builder("distributed.lock.timeouts")
            .description("分散式鎖超時次數")
            .register(meterRegistry);
        
        // 初始化計時器
        this.lockAcquisitionTimer = Timer.builder("distributed.lock.acquisition.duration")
            .description("分散式鎖獲取耗時")
            .register(meterRegistry);
            
        this.lockHoldTimer = Timer.builder("distributed.lock.hold.duration")
            .description("分散式鎖持有時間")
            .register(meterRegistry);
        
        // 初始化 Gauge
        Gauge.builder("distributed.lock.active.count", activeLocks, AtomicLong::get)
            .description("當前活躍的分散式鎖數量")
            .register(meterRegistry);
            
        Gauge.builder("distributed.lock.total.acquired", totalLocksAcquired, AtomicLong::get)
            .description("總共獲取的分散式鎖數量")
            .register(meterRegistry);
            
        Gauge.builder("distributed.lock.total.released", totalLocksReleased, AtomicLong::get)
            .description("總共釋放的分散式鎖數量")
            .register(meterRegistry);
    }

    /**
     * 記錄鎖獲取嘗試
     */
    public void recordLockAcquisitionAttempt(String lockType, String lockKey) {
        lockAcquisitionAttempts.increment();
        getLockTypeCounter(lockType, "attempts").increment();
        logger.debug("記錄鎖獲取嘗試: type={}, key={}", lockType, lockKey);
    }

    /**
     * 記錄鎖獲取成功
     */
    public void recordLockAcquisitionSuccess(String lockType, String lockKey, Duration duration) {
        lockAcquisitionSuccess.increment();
        lockAcquisitionTimer.record(duration);
        activeLocks.incrementAndGet();
        totalLocksAcquired.incrementAndGet();
        
        getLockTypeCounter(lockType, "success").increment();
        getLockTypeTimer(lockType, "acquisition").record(duration);
        
        logger.debug("記錄鎖獲取成功: type={}, key={}, duration={}ms", 
                    lockType, lockKey, duration.toMillis());
    }

    /**
     * 記錄鎖獲取失敗
     */
    public void recordLockAcquisitionFailure(String lockType, String lockKey, String reason, Duration duration) {
        lockAcquisitionFailures.increment();
        getLockTypeCounter(lockType, "failures").increment();
        
        if ("timeout".equals(reason)) {
            lockTimeouts.increment();
        }
        
        logger.debug("記錄鎖獲取失敗: type={}, key={}, reason={}, duration={}ms", 
                    lockType, lockKey, reason, duration.toMillis());
    }

    /**
     * 記錄鎖釋放嘗試
     */
    public void recordLockReleaseAttempt(String lockType, String lockKey) {
        lockReleaseAttempts.increment();
        getLockTypeCounter(lockType, "release_attempts").increment();
        logger.debug("記錄鎖釋放嘗試: type={}, key={}", lockType, lockKey);
    }

    /**
     * 記錄鎖釋放成功
     */
    public void recordLockReleaseSuccess(String lockType, String lockKey, Duration holdDuration) {
        lockReleaseSuccess.increment();
        lockHoldTimer.record(holdDuration);
        activeLocks.decrementAndGet();
        totalLocksReleased.incrementAndGet();
        
        getLockTypeCounter(lockType, "release_success").increment();
        getLockTypeTimer(lockType, "hold").record(holdDuration);
        
        logger.debug("記錄鎖釋放成功: type={}, key={}, holdDuration={}ms", 
                    lockType, lockKey, holdDuration.toMillis());
    }

    /**
     * 記錄鎖釋放失敗
     */
    public void recordLockReleaseFailure(String lockType, String lockKey, String reason) {
        lockReleaseFailures.increment();
        getLockTypeCounter(lockType, "release_failures").increment();
        
        logger.warn("記錄鎖釋放失敗: type={}, key={}, reason={}", lockType, lockKey, reason);
    }

    /**
     * 獲取按鎖類型分組的計數器
     */
    private Counter getLockTypeCounter(String lockType, String operation) {
        String key = lockType + "." + operation;
        return lockTypeCounters.computeIfAbsent(key, k -> 
            Counter.builder("distributed.lock." + operation + ".by.type")
                .description("按類型分組的分散式鎖" + operation + "指標")
                .tag("lock.type", lockType)
                .register(meterRegistry)
        );
    }

    /**
     * 獲取按鎖類型分組的計時器
     */
    private Timer getLockTypeTimer(String lockType, String operation) {
        String key = lockType + "." + operation;
        return lockTypeTimers.computeIfAbsent(key, k -> 
            Timer.builder("distributed.lock." + operation + ".duration.by.type")
                .description("按類型分組的分散式鎖" + operation + "耗時")
                .tag("lock.type", lockType)
                .register(meterRegistry)
        );
    }

    /**
     * 獲取當前活躍鎖數量
     */
    public long getActiveLockCount() {
        return activeLocks.get();
    }

    /**
     * 獲取總獲取鎖數量
     */
    public long getTotalAcquiredCount() {
        return totalLocksAcquired.get();
    }

    /**
     * 獲取總釋放鎖數量
     */
    public long getTotalReleasedCount() {
        return totalLocksReleased.get();
    }

    /**
     * 獲取鎖獲取成功率
     */
    public double getLockAcquisitionSuccessRate() {
        double attempts = lockAcquisitionAttempts.count();
        double successes = lockAcquisitionSuccess.count();
        return attempts > 0 ? (successes / attempts) * 100.0 : 0.0;
    }

    /**
     * 獲取鎖釋放成功率
     */
    public double getLockReleaseSuccessRate() {
        double attempts = lockReleaseAttempts.count();
        double successes = lockReleaseSuccess.count();
        return attempts > 0 ? (successes / attempts) * 100.0 : 0.0;
    }
}