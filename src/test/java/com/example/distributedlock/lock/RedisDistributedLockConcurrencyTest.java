package com.example.distributedlock.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Redis 分散式鎖併發測試
 * 驗證鎖的超時和重試邏輯
 */
@ExtendWith(MockitoExtension.class)
class RedisDistributedLockConcurrencyTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Test
    void testLockTimeoutAndRetryLogic() throws InterruptedException {
        // Given
        RedisDistributedLock redisDistributedLock = new RedisDistributedLock(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        
        // 模擬第一次嘗試失敗，第二次成功
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false)  // 第一次失敗
                .thenReturn(true);  // 第二次成功

        String lockKey = "test-lock";
        
        // When - 第一次嘗試（應該失敗）
        boolean firstAttempt = redisDistributedLock.tryLock(lockKey, 1, 10, TimeUnit.SECONDS);
        
        // When - 第二次嘗試（應該成功）
        boolean secondAttempt = redisDistributedLock.tryLock(lockKey + "2", 1, 10, TimeUnit.SECONDS);

        // Then
        assertFalse(firstAttempt, "第一次嘗試應該失敗");
        assertTrue(secondAttempt, "第二次嘗試應該成功");
        
        // 驗證 tryLock 被調用了兩次
        verify(rLock, times(2)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testConcurrentLockAcquisition() throws InterruptedException {
        // Given
        RedisDistributedLock redisDistributedLock = new RedisDistributedLock(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // 只有一個執行緒能成功獲取鎖
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true)   // 第一個成功
                .thenReturn(false)  // 其他都失敗
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(false);

        // When
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    boolean acquired = redisDistributedLock.tryLock("concurrent-lock", 1, 10, TimeUnit.SECONDS);
                    if (acquired) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 開始所有執行緒
        completeLatch.await(5, TimeUnit.SECONDS); // 等待所有執行緒完成

        // Then
        assertEquals(1, successCount.get(), "只有一個執行緒應該成功獲取鎖");
        verify(rLock, times(threadCount)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testLockLeaseTimeExpiration() throws InterruptedException {
        // Given
        RedisDistributedLock redisDistributedLock = new RedisDistributedLock(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        String lockKey = "lease-test-lock";
        long leaseTime = 2; // 2 秒租約時間

        // When
        boolean acquired = redisDistributedLock.tryLock(lockKey, 1, leaseTime, TimeUnit.SECONDS);
        
        // Then
        assertTrue(acquired, "應該成功獲取鎖");
        
        // 驗證使用了正確的租約時間
        verify(rLock).tryLock(1, leaseTime, TimeUnit.SECONDS);
        
        // 驗證鎖被正確記錄
        assertEquals(1, redisDistributedLock.getHeldLocksCount());
    }

    @Test
    void testExceptionHandlingInTryLock() throws InterruptedException {
        // Given
        RedisDistributedLock redisDistributedLock = new RedisDistributedLock(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));

        String lockKey = "exception-test-lock";

        // When
        boolean result = redisDistributedLock.tryLock(lockKey, 1, 10, TimeUnit.SECONDS);

        // Then
        assertFalse(result, "當發生異常時應該返回 false");
        assertEquals(0, redisDistributedLock.getHeldLocksCount(), "異常時不應該記錄鎖");
    }

    @Test
    void testUnlockWithoutHoldingLock() {
        // Given
        RedisDistributedLock redisDistributedLock = new RedisDistributedLock(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        String lockKey = "not-held-lock";

        // When
        redisDistributedLock.unlock(lockKey);

        // Then
        verify(rLock, never()).unlock();
        verify(rLock).isHeldByCurrentThread();
    }
}