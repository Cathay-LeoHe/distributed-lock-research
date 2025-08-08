package com.example.distributedlock.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Redis 分散式鎖測試類別
 */
@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private RedisDistributedLock redisDistributedLock;

    @BeforeEach
    void setUp() {
        redisDistributedLock = new RedisDistributedLock(redissonClient);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @Test
    void testTryLock_Success() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        long waitTime = 5;
        long leaseTime = 10;
        TimeUnit unit = TimeUnit.SECONDS;
        
        when(rLock.tryLock(waitTime, leaseTime, unit)).thenReturn(true);

        // When
        boolean result = redisDistributedLock.tryLock(lockKey, waitTime, leaseTime, unit);

        // Then
        assertTrue(result);
        verify(redissonClient).getLock(lockKey);
        verify(rLock).tryLock(waitTime, leaseTime, unit);
        assertEquals(1, redisDistributedLock.getHeldLocksCount());
    }

    @Test
    void testTryLock_Failure() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        long waitTime = 5;
        long leaseTime = 10;
        TimeUnit unit = TimeUnit.SECONDS;
        
        when(rLock.tryLock(waitTime, leaseTime, unit)).thenReturn(false);

        // When
        boolean result = redisDistributedLock.tryLock(lockKey, waitTime, leaseTime, unit);

        // Then
        assertFalse(result);
        verify(redissonClient).getLock(lockKey);
        verify(rLock).tryLock(waitTime, leaseTime, unit);
        assertEquals(0, redisDistributedLock.getHeldLocksCount());
    }

    @Test
    void testTryLock_NullKey() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            redisDistributedLock.tryLock(null, 5, 10, TimeUnit.SECONDS);
        });
    }

    @Test
    void testTryLock_EmptyKey() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            redisDistributedLock.tryLock("", 5, 10, TimeUnit.SECONDS);
        });
    }

    @Test
    void testTryLock_InterruptedException() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        long waitTime = 5;
        long leaseTime = 10;
        TimeUnit unit = TimeUnit.SECONDS;
        
        when(rLock.tryLock(waitTime, leaseTime, unit)).thenThrow(new InterruptedException("Test interruption"));

        // When & Then
        assertThrows(InterruptedException.class, () -> {
            redisDistributedLock.tryLock(lockKey, waitTime, leaseTime, unit);
        });
        
        verify(redissonClient).getLock(lockKey);
        verify(rLock).tryLock(waitTime, leaseTime, unit);
    }

    @Test
    void testUnlock_Success() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        // 先獲取鎖
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        
        redisDistributedLock.tryLock(lockKey, 5, 10, TimeUnit.SECONDS);

        // When
        redisDistributedLock.unlock(lockKey);

        // Then
        verify(rLock).unlock();
        assertEquals(0, redisDistributedLock.getHeldLocksCount());
    }

    @Test
    void testUnlock_NotHeldByCurrentThread() {
        // Given
        String lockKey = "test-lock";
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        // When
        redisDistributedLock.unlock(lockKey);

        // Then
        verify(rLock, never()).unlock();
    }

    @Test
    void testUnlock_NullKey() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            redisDistributedLock.unlock(null);
        });
    }

    @Test
    void testIsLocked() {
        // Given
        String lockKey = "test-lock";
        when(rLock.isLocked()).thenReturn(true);

        // When
        boolean result = redisDistributedLock.isLocked(lockKey);

        // Then
        assertTrue(result);
        verify(redissonClient).getLock(lockKey);
        verify(rLock).isLocked();
    }

    @Test
    void testIsLocked_NullKey() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            redisDistributedLock.isLocked(null);
        });
    }

    @Test
    void testIsHeldByCurrentThread() {
        // Given
        String lockKey = "test-lock";
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // When
        boolean result = redisDistributedLock.isHeldByCurrentThread(lockKey);

        // Then
        assertTrue(result);
        verify(rLock).isHeldByCurrentThread();
    }

    @Test
    void testIsHeldByCurrentThread_NullKey() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            redisDistributedLock.isHeldByCurrentThread(null);
        });
    }

    @Test
    void testForceUnlock() {
        // Given
        String lockKey = "test-lock";
        when(rLock.isLocked()).thenReturn(true);

        // When
        redisDistributedLock.forceUnlock(lockKey);

        // Then
        verify(redissonClient).getLock(lockKey);
        verify(rLock).isLocked();
        verify(rLock).forceUnlock();
    }

    @Test
    void testClearHeldLocks() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        when(rLock.tryLock(5, 10, TimeUnit.SECONDS)).thenReturn(true);
        
        redisDistributedLock.tryLock(lockKey, 5, 10, TimeUnit.SECONDS);
        assertEquals(1, redisDistributedLock.getHeldLocksCount());

        // When
        redisDistributedLock.clearHeldLocks();

        // Then
        assertEquals(0, redisDistributedLock.getHeldLocksCount());
    }
}