package com.example.distributedlock.factory;

import com.example.distributedlock.config.DistributedLockProperties;
import com.example.distributedlock.lock.DistributedLock;
import com.example.distributedlock.lock.RedisDistributedLock;
import com.example.distributedlock.lock.ZooKeeperDistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 分散式鎖工廠測試類別
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockFactoryTest {

    @Mock
    private DistributedLockProperties lockProperties;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private RedisDistributedLock redisDistributedLock;

    @Mock
    private ZooKeeperDistributedLock zooKeeperDistributedLock;

    private DistributedLockFactory lockFactory;

    @BeforeEach
    void setUp() {
        lockFactory = new DistributedLockFactory(lockProperties, applicationContext);
    }

    @Test
    void testInitializeRedisLock() {
        // Given
        when(lockProperties.getProvider()).thenReturn("redis");
        when(applicationContext.getBean(RedisDistributedLock.class)).thenReturn(redisDistributedLock);

        // When
        lockFactory.initializeLock();

        // Then
        DistributedLock result = lockFactory.getDistributedLock();
        assertNotNull(result);
        assertEquals(redisDistributedLock, result);
        verify(applicationContext).getBean(RedisDistributedLock.class);
    }

    @Test
    void testInitializeZooKeeperLock() {
        // Given
        when(lockProperties.getProvider()).thenReturn("zookeeper");
        when(applicationContext.getBean(ZooKeeperDistributedLock.class)).thenReturn(zooKeeperDistributedLock);

        // When
        lockFactory.initializeLock();

        // Then
        DistributedLock result = lockFactory.getDistributedLock();
        assertNotNull(result);
        assertEquals(zooKeeperDistributedLock, result);
        verify(applicationContext).getBean(ZooKeeperDistributedLock.class);
    }

    @Test
    void testInitializeWithInvalidProvider() {
        // Given
        when(lockProperties.getProvider()).thenReturn("invalid");
        when(applicationContext.getBean(RedisDistributedLock.class)).thenReturn(redisDistributedLock);

        // When
        lockFactory.initializeLock();

        // Then
        DistributedLock result = lockFactory.getDistributedLock();
        assertNotNull(result);
        assertEquals(redisDistributedLock, result); // 應該回退到 Redis
        verify(applicationContext).getBean(RedisDistributedLock.class);
    }

    @Test
    void testGetDistributedLockBeforeInitialization() {
        // When & Then
        assertThrows(IllegalStateException.class, () -> lockFactory.getDistributedLock());
    }

    @Test
    void testSwitchLockProvider() {
        // Given - 初始化為 Redis
        when(lockProperties.getProvider()).thenReturn("redis");
        when(applicationContext.getBean(RedisDistributedLock.class)).thenReturn(redisDistributedLock);
        lockFactory.initializeLock();

        // 設置切換後的行為 - 第一次調用返回 "redis"，後續調用返回 "zookeeper"
        when(lockProperties.getProvider())
            .thenReturn("redis")  // 用於比較當前提供者
            .thenReturn("zookeeper");  // 用於重新初始化
        when(applicationContext.getBean(ZooKeeperDistributedLock.class)).thenReturn(zooKeeperDistributedLock);

        // When
        lockFactory.switchLockProvider("zookeeper");

        // Then
        DistributedLock result = lockFactory.getDistributedLock();
        assertEquals(zooKeeperDistributedLock, result);
        verify(lockProperties).setProvider("zookeeper");
        verify(applicationContext).getBean(ZooKeeperDistributedLock.class);
    }

    @Test
    void testSwitchToSameProvider() {
        // Given
        when(lockProperties.getProvider()).thenReturn("redis");
        when(applicationContext.getBean(RedisDistributedLock.class)).thenReturn(redisDistributedLock);
        lockFactory.initializeLock();

        // When
        lockFactory.switchLockProvider("redis");

        // Then
        DistributedLock result = lockFactory.getDistributedLock();
        assertEquals(redisDistributedLock, result);
        // 不應該重新設置提供者
        verify(lockProperties, never()).setProvider(anyString());
    }

    @Test
    void testSwitchLockProviderWithNullProvider() {
        // Given
        when(lockProperties.getProvider()).thenReturn("redis");
        when(applicationContext.getBean(RedisDistributedLock.class)).thenReturn(redisDistributedLock);
        lockFactory.initializeLock();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> lockFactory.switchLockProvider(null));
        assertThrows(IllegalArgumentException.class, () -> lockFactory.switchLockProvider(""));
        assertThrows(IllegalArgumentException.class, () -> lockFactory.switchLockProvider("   "));
    }

    @Test
    void testGetCurrentProvider() {
        // Given
        when(lockProperties.getProvider()).thenReturn("redis");

        // When
        String provider = lockFactory.getCurrentProvider();

        // Then
        assertEquals("redis", provider);
        verify(lockProperties).getProvider();
    }

    @Test
    void testIsProviderAvailable() {
        // Given
        when(applicationContext.getBeansOfType(RedisDistributedLock.class))
                .thenReturn(java.util.Collections.singletonMap("redisLock", redisDistributedLock));
        when(applicationContext.getBeansOfType(ZooKeeperDistributedLock.class))
                .thenReturn(java.util.Collections.emptyMap());

        // When & Then
        assertTrue(lockFactory.isProviderAvailable("redis"));
        assertFalse(lockFactory.isProviderAvailable("zookeeper"));
        assertFalse(lockFactory.isProviderAvailable("invalid"));
        assertFalse(lockFactory.isProviderAvailable(null));
        assertFalse(lockFactory.isProviderAvailable(""));
    }

    @Test
    void testGetLockStatistics() {
        // Given
        when(lockProperties.getProvider()).thenReturn("redis");
        when(applicationContext.getBean(RedisDistributedLock.class)).thenReturn(redisDistributedLock);
        when(redisDistributedLock.getHeldLocksCount()).thenReturn(5);
        lockFactory.initializeLock();

        // When
        DistributedLockFactory.LockStatistics stats = lockFactory.getLockStatistics();

        // Then
        assertNotNull(stats);
        assertEquals("redis", stats.getProvider());
        assertEquals(5, stats.getLockCount());
    }

    @Test
    void testGetLockStatisticsWithZooKeeper() {
        // Given
        when(lockProperties.getProvider()).thenReturn("zookeeper");
        when(applicationContext.getBean(ZooKeeperDistributedLock.class)).thenReturn(zooKeeperDistributedLock);
        when(zooKeeperDistributedLock.getCachedLockCount()).thenReturn(3);
        lockFactory.initializeLock();

        // When
        DistributedLockFactory.LockStatistics stats = lockFactory.getLockStatistics();

        // Then
        assertNotNull(stats);
        assertEquals("zookeeper", stats.getProvider());
        assertEquals(3, stats.getLockCount());
    }

    @Test
    void testGetLockStatisticsBeforeInitialization() {
        // When
        DistributedLockFactory.LockStatistics stats = lockFactory.getLockStatistics();

        // Then
        assertNotNull(stats);
        assertEquals("unknown", stats.getProvider());
        assertEquals(0, stats.getLockCount());
    }

    @Test
    void testLockStatisticsToString() {
        // Given
        DistributedLockFactory.LockStatistics stats = new DistributedLockFactory.LockStatistics("redis", 5);

        // When
        String result = stats.toString();

        // Then
        assertEquals("LockStatistics{provider='redis', lockCount=5}", result);
    }
}