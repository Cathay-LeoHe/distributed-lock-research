package com.example.distributedlock.lock;

import com.example.distributedlock.metrics.LockMetrics;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ZooKeeper 分散式鎖實作
 * 使用 Apache Curator 的 InterProcessMutex 實現基於臨時順序節點的分散式鎖
 */
@Component
public class ZooKeeperDistributedLock implements DistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperDistributedLock.class);
    
    private static final String LOCK_ROOT_PATH = "/distributed-locks";
    
    private final CuratorFramework curatorFramework;
    private final LockMetrics lockMetrics;
    private final ConcurrentHashMap<String, InterProcessMutex> lockCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockAcquisitionTimes = new ConcurrentHashMap<>();

    public ZooKeeperDistributedLock(CuratorFramework curatorFramework, @Autowired(required = false) LockMetrics lockMetrics) {
        this.curatorFramework = curatorFramework;
        this.lockMetrics = lockMetrics;
        logger.info("ZooKeeper 分散式鎖初始化完成");
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("鎖鍵不能為空");
        }

        Instant startTime = Instant.now();
        
        // 記錄鎖獲取嘗試
        if (lockMetrics != null) {
            lockMetrics.recordLockAcquisitionAttempt("zookeeper", lockKey);
        }

        String lockPath = LOCK_ROOT_PATH + "/" + lockKey;
        InterProcessMutex mutex = lockCache.computeIfAbsent(lockKey, k -> new InterProcessMutex(curatorFramework, lockPath));

        try {
            logger.debug("嘗試獲取 ZooKeeper 鎖: {}, 等待時間: {} {}", lockKey, waitTime, unit);
            
            boolean acquired = mutex.acquire(waitTime, unit);
            Duration duration = Duration.between(startTime, Instant.now());
            
            if (acquired) {
                // 記錄獲取時間
                lockAcquisitionTimes.put(lockKey, startTime);
                
                // 記錄鎖獲取成功
                if (lockMetrics != null) {
                    lockMetrics.recordLockAcquisitionSuccess("zookeeper", lockKey, duration);
                }
                
                logger.debug("成功獲取 ZooKeeper 鎖: {} in {}ms", lockKey, duration.toMillis());
                
                // 注意：ZooKeeper 的 InterProcessMutex 不支持租約時間（leaseTime）
                // 鎖會在會話結束或手動釋放時自動釋放
                if (leaseTime > 0) {
                    logger.warn("ZooKeeper 分散式鎖不支持租約時間，鎖將在會話結束時自動釋放: {}", lockKey);
                }
            } else {
                // 記錄鎖獲取失敗
                if (lockMetrics != null) {
                    lockMetrics.recordLockAcquisitionFailure("zookeeper", lockKey, "timeout", duration);
                }
                
                logger.debug("獲取 ZooKeeper 鎖超時: {} after {}ms", lockKey, duration.toMillis());
            }
            
            return acquired;
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            
            // 記錄鎖獲取失敗
            if (lockMetrics != null) {
                lockMetrics.recordLockAcquisitionFailure("zookeeper", lockKey, "exception", duration);
            }
            
            logger.error("獲取 ZooKeeper 鎖時發生異常: {} after {}ms", lockKey, duration.toMillis(), e);
            throw new RuntimeException("獲取 ZooKeeper 鎖失敗: " + lockKey, e);
        }
    }

    @Override
    public void unlock(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("鎖鍵不能為空");
        }

        // 記錄鎖釋放嘗試
        if (lockMetrics != null) {
            lockMetrics.recordLockReleaseAttempt("zookeeper", lockKey);
        }

        InterProcessMutex mutex = lockCache.get(lockKey);
        if (mutex == null) {
            // 記錄鎖釋放失敗
            if (lockMetrics != null) {
                lockMetrics.recordLockReleaseFailure("zookeeper", lockKey, "lock_not_found");
            }
            
            logger.warn("嘗試釋放不存在的鎖: {}", lockKey);
            return;
        }

        try {
            Instant acquisitionTime = lockAcquisitionTimes.get(lockKey);
            
            logger.debug("釋放 ZooKeeper 鎖: {}", lockKey);
            mutex.release();
            
            // 計算鎖持有時間
            Duration holdDuration = acquisitionTime != null ? 
                Duration.between(acquisitionTime, Instant.now()) : Duration.ZERO;
            
            // 清理記錄
            lockAcquisitionTimes.remove(lockKey);
            
            // 記錄鎖釋放成功
            if (lockMetrics != null) {
                lockMetrics.recordLockReleaseSuccess("zookeeper", lockKey, holdDuration);
            }
            
            logger.debug("成功釋放 ZooKeeper 鎖: {} after holding for {}ms", lockKey, holdDuration.toMillis());
        } catch (Exception e) {
            // 記錄鎖釋放失敗
            if (lockMetrics != null) {
                lockMetrics.recordLockReleaseFailure("zookeeper", lockKey, "exception");
            }
            
            // 清理記錄
            lockAcquisitionTimes.remove(lockKey);
            
            logger.error("釋放 ZooKeeper 鎖時發生異常: {}", lockKey, e);
            throw new RuntimeException("釋放 ZooKeeper 鎖失敗: " + lockKey, e);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("鎖鍵不能為空");
        }

        InterProcessMutex mutex = lockCache.get(lockKey);
        if (mutex == null) {
            return false;
        }

        try {
            // InterProcessMutex 沒有直接的 isLocked 方法
            // 我們通過檢查是否能立即獲取鎖來判斷鎖狀態
            boolean canAcquire = mutex.acquire(0, TimeUnit.MILLISECONDS);
            if (canAcquire) {
                // 如果能獲取到鎖，說明之前沒有被鎖定，立即釋放
                mutex.release();
                return false;
            } else {
                // 如果不能獲取到鎖，說明被其他線程持有
                return true;
            }
        } catch (Exception e) {
            logger.error("檢查 ZooKeeper 鎖狀態時發生異常: {}", lockKey, e);
            return false;
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("鎖鍵不能為空");
        }

        InterProcessMutex mutex = lockCache.get(lockKey);
        if (mutex == null) {
            return false;
        }

        try {
            // InterProcessMutex 提供了 isOwnedByCurrentThread 方法
            return mutex.isOwnedByCurrentThread();
        } catch (Exception e) {
            logger.error("檢查 ZooKeeper 鎖持有狀態時發生異常: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 清理鎖快取中的指定鎖
     * 
     * @param lockKey 鎖鍵
     */
    public void cleanupLock(String lockKey) {
        if (lockKey != null) {
            InterProcessMutex mutex = lockCache.remove(lockKey);
            if (mutex != null) {
                try {
                    if (mutex.isOwnedByCurrentThread()) {
                        mutex.release();
                    }
                    logger.debug("清理 ZooKeeper 鎖快取: {}", lockKey);
                } catch (Exception e) {
                    logger.warn("清理 ZooKeeper 鎖時發生異常: {}", lockKey, e);
                }
            }
        }
    }

    /**
     * 清理所有鎖快取
     */
    public void cleanupAllLocks() {
        logger.info("清理所有 ZooKeeper 鎖快取");
        lockCache.forEach((key, mutex) -> {
            try {
                if (mutex.isOwnedByCurrentThread()) {
                    mutex.release();
                }
            } catch (Exception e) {
                logger.warn("清理 ZooKeeper 鎖時發生異常: {}", key, e);
            }
        });
        lockCache.clear();
    }

    /**
     * 獲取當前快取的鎖數量
     * 
     * @return 鎖數量
     */
    public int getCachedLockCount() {
        return lockCache.size();
    }
}