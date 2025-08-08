package com.example.distributedlock.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分散式鎖抽象介面
 * 定義分散式鎖的基本操作方法
 */
public interface DistributedLock {
    
    /**
     * 嘗試獲取鎖
     * 
     * @param lockKey 鎖的唯一標識
     * @param waitTime 等待時間
     * @param leaseTime 鎖持有時間
     * @param unit 時間單位
     * @return 是否成功獲取鎖
     * @throws InterruptedException 當等待被中斷時拋出
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
    
    /**
     * 釋放鎖
     * 
     * @param lockKey 鎖的唯一標識
     */
    void unlock(String lockKey);
    
    /**
     * 檢查鎖是否被持有
     * 
     * @param lockKey 鎖的唯一標識
     * @return 鎖是否被持有
     */
    boolean isLocked(String lockKey);
    
    /**
     * 檢查鎖是否被當前執行緒持有
     * 
     * @param lockKey 鎖的唯一標識
     * @return 鎖是否被當前執行緒持有
     */
    boolean isHeldByCurrentThread(String lockKey);
}