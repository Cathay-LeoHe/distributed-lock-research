package com.example.distributedlock.lock;

import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZooKeeper 分散式鎖簡單測試
 * 主要測試基本功能和異常處理
 */
@ExtendWith(MockitoExtension.class)
class ZooKeeperDistributedLockSimpleTest {

    @Mock
    private CuratorFramework curatorFramework;

    private ZooKeeperDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        distributedLock = new ZooKeeperDistributedLock(curatorFramework);
    }

    @Test
    void testTryLockWithNullKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.tryLock(null, 5, 10, TimeUnit.SECONDS);
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testTryLockWithEmptyKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.tryLock("", 5, 10, TimeUnit.SECONDS);
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testTryLockWithWhitespaceKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.tryLock("   ", 5, 10, TimeUnit.SECONDS);
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testUnlockWithNullKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.unlock(null);
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testUnlockWithEmptyKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.unlock("");
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testUnlockNonExistentLock() {
        // 嘗試釋放不存在的鎖，應該不拋出異常
        assertDoesNotThrow(() -> {
            distributedLock.unlock("non-existent-lock");
        });
    }

    @Test
    void testIsLockedWithNullKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.isLocked(null);
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testIsLockedForNonExistentLock() {
        boolean result = distributedLock.isLocked("non-existent-lock");
        assertFalse(result);
    }

    @Test
    void testIsHeldByCurrentThreadWithNullKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            distributedLock.isHeldByCurrentThread(null);
        });

        assertEquals("鎖鍵不能為空", exception.getMessage());
    }

    @Test
    void testIsHeldByCurrentThreadForNonExistentLock() {
        boolean result = distributedLock.isHeldByCurrentThread("non-existent-lock");
        assertFalse(result);
    }

    @Test
    void testCleanupLockWithNullKey() {
        // 清理 null 鍵應該不拋出異常
        assertDoesNotThrow(() -> {
            distributedLock.cleanupLock(null);
        });
    }

    @Test
    void testCleanupAllLocks() {
        // 清理所有鎖應該不拋出異常
        assertDoesNotThrow(() -> {
            distributedLock.cleanupAllLocks();
        });
    }

    @Test
    void testGetCachedLockCountInitially() {
        assertEquals(0, distributedLock.getCachedLockCount());
    }

    @Test
    void testConstructorWithNullCuratorFramework() {
        // 測試構造函數可以接受 null（雖然實際使用時會失敗）
        assertDoesNotThrow(() -> {
            new ZooKeeperDistributedLock(null);
        });
    }
}