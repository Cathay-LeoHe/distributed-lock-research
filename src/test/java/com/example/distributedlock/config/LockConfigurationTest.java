package com.example.distributedlock.config;

import com.example.distributedlock.factory.DistributedLockFactory;
import com.example.distributedlock.lock.DistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 鎖配置測試類別
 */
@ExtendWith(MockitoExtension.class)
class LockConfigurationTest {

    @Mock
    private DistributedLockFactory lockFactory;

    @Mock
    private DistributedLock distributedLock;

    private LockConfiguration lockConfiguration;
    private LockConfiguration.LockManager lockManager;

    @BeforeEach
    void setUp() {
        lockConfiguration = new LockConfiguration();
        lockManager = new LockConfiguration.LockManager(lockFactory);
    }

    @Test
    void testDistributedLockBean() {
        // Given
        when(lockFactory.getDistributedLock()).thenReturn(distributedLock);

        // When
        DistributedLock result = lockConfiguration.distributedLock(lockFactory);

        // Then
        assertNotNull(result);
        assertEquals(distributedLock, result);
        verify(lockFactory).getDistributedLock();
    }

    @Test
    void testLockManagerBean() {
        // When
        LockConfiguration.LockManager result = lockConfiguration.lockManager(lockFactory);

        // Then
        assertNotNull(result);
    }

    @Test
    void testLockManagerSwitchProvider() {
        // Given
        when(lockFactory.isProviderAvailable("redis")).thenReturn(true);

        // When
        boolean result = lockManager.switchProvider("redis");

        // Then
        assertTrue(result);
        verify(lockFactory).switchLockProvider("redis");
    }

    @Test
    void testLockManagerSwitchProviderWithInvalidProvider() {
        // When
        boolean result = lockManager.switchProvider("invalid");

        // Then
        assertFalse(result);
        verify(lockFactory, never()).switchLockProvider(anyString());
    }

    @Test
    void testLockManagerSwitchProviderWithNullProvider() {
        // When
        boolean result = lockManager.switchProvider(null);

        // Then
        assertFalse(result);
        verify(lockFactory, never()).switchLockProvider(anyString());
    }

    @Test
    void testLockManagerSwitchProviderWithEmptyProvider() {
        // When
        boolean result = lockManager.switchProvider("");

        // Then
        assertFalse(result);
        verify(lockFactory, never()).switchLockProvider(anyString());
    }

    @Test
    void testLockManagerSwitchProviderUnavailable() {
        // Given
        when(lockFactory.isProviderAvailable("redis")).thenReturn(false);

        // When
        boolean result = lockManager.switchProvider("redis");

        // Then
        assertFalse(result);
        verify(lockFactory, never()).switchLockProvider(anyString());
    }

    @Test
    void testLockManagerSwitchProviderException() {
        // Given
        when(lockFactory.isProviderAvailable("redis")).thenReturn(true);
        doThrow(new RuntimeException("Switch failed")).when(lockFactory).switchLockProvider("redis");

        // When
        boolean result = lockManager.switchProvider("redis");

        // Then
        assertFalse(result);
        verify(lockFactory).switchLockProvider("redis");
    }

    @Test
    void testLockManagerGetCurrentProvider() {
        // Given
        when(lockFactory.getCurrentProvider()).thenReturn("redis");

        // When
        String result = lockManager.getCurrentProvider();

        // Then
        assertEquals("redis", result);
        verify(lockFactory).getCurrentProvider();
    }

    @Test
    void testLockManagerIsProviderAvailable() {
        // Given
        when(lockFactory.isProviderAvailable("redis")).thenReturn(true);

        // When
        boolean result = lockManager.isProviderAvailable("redis");

        // Then
        assertTrue(result);
        verify(lockFactory).isProviderAvailable("redis");
    }

    @Test
    void testLockManagerGetLockStatistics() {
        // Given
        DistributedLockFactory.LockStatistics stats = new DistributedLockFactory.LockStatistics("redis", 5);
        when(lockFactory.getLockStatistics()).thenReturn(stats);

        // When
        DistributedLockFactory.LockStatistics result = lockManager.getLockStatistics();

        // Then
        assertNotNull(result);
        assertEquals(stats, result);
        verify(lockFactory).getLockStatistics();
    }

    @Test
    void testLockManagerGetSupportedProviders() {
        // When
        String[] result = lockManager.getSupportedProviders();

        // Then
        assertNotNull(result);
        assertEquals(2, result.length);
        assertArrayEquals(new String[]{"redis", "zookeeper"}, result);
    }

    @Test
    void testLockManagerGetProviderStatus() {
        // Given
        when(lockFactory.getCurrentProvider()).thenReturn("redis");
        when(lockFactory.getLockStatistics()).thenReturn(new DistributedLockFactory.LockStatistics("redis", 3));
        when(lockFactory.isProviderAvailable("redis")).thenReturn(true);
        when(lockFactory.isProviderAvailable("zookeeper")).thenReturn(false);

        // When
        LockConfiguration.LockProviderStatus result = lockManager.getProviderStatus();

        // Then
        assertNotNull(result);
        assertEquals("redis", result.getCurrentProvider());
        assertEquals(3, result.getActiveLocks());
        assertTrue(result.isRedisAvailable());
        assertFalse(result.isZookeeperAvailable());
    }

    @Test
    void testLockProviderStatusToString() {
        // Given
        LockConfiguration.LockProviderStatus status = new LockConfiguration.LockProviderStatus(
                "redis", 5, true, false);

        // When
        String result = status.toString();

        // Then
        assertEquals("LockProviderStatus{currentProvider='redis', activeLocks=5, redisAvailable=true, zookeeperAvailable=false}", result);
    }

    @Test
    void testIsValidProvider() {
        // 使用反射測試私有方法的邏輯，通過公共方法間接測試
        assertTrue(lockManager.switchProvider("redis") || !lockManager.isProviderAvailable("redis"));
        assertTrue(lockManager.switchProvider("zookeeper") || !lockManager.isProviderAvailable("zookeeper"));
        assertFalse(lockManager.switchProvider("invalid"));
        assertFalse(lockManager.switchProvider(null));
        assertFalse(lockManager.switchProvider(""));
        assertFalse(lockManager.switchProvider("   "));
    }
}