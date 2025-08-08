package com.example.distributedlock.config;

import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ZooKeeper 配置測試
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "distributed-lock.provider=zookeeper",
    "distributed-lock.zookeeper.connect-string=localhost:2181",
    "distributed-lock.zookeeper.session-timeout=30000",
    "distributed-lock.zookeeper.connection-timeout=10000",
    "distributed-lock.zookeeper.retry-policy.base-sleep-time=500",
    "distributed-lock.zookeeper.retry-policy.max-retries=2"
})
class ZooKeeperConfigTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public CuratorFramework mockCuratorFramework() {
            CuratorFramework mock = mock(CuratorFramework.class);
            try {
                when(mock.blockUntilConnected(anyInt(), any())).thenReturn(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mock;
        }
    }

    @Autowired
    private ZooKeeperConfig.ZooKeeperProperties zooKeeperProperties;

    @Test
    void testZooKeeperPropertiesConfiguration() {
        assertNotNull(zooKeeperProperties);
        assertEquals("localhost:2181", zooKeeperProperties.getConnectString());
        assertEquals(30000, zooKeeperProperties.getSessionTimeout());
        assertEquals(10000, zooKeeperProperties.getConnectionTimeout());
        assertEquals(500, zooKeeperProperties.getRetryPolicy().getBaseSleepTime());
        assertEquals(2, zooKeeperProperties.getRetryPolicy().getMaxRetries());
    }

    @Test
    void testRetryPolicyDefaults() {
        ZooKeeperConfig.ZooKeeperProperties.RetryPolicy retryPolicy = 
            new ZooKeeperConfig.ZooKeeperProperties.RetryPolicy();
        
        assertEquals(1000, retryPolicy.getBaseSleepTime());
        assertEquals(3, retryPolicy.getMaxRetries());
    }

    @Test
    void testZooKeeperPropertiesDefaults() {
        ZooKeeperConfig.ZooKeeperProperties properties = 
            new ZooKeeperConfig.ZooKeeperProperties();
        
        assertEquals("localhost:2181", properties.getConnectString());
        assertEquals(60000, properties.getSessionTimeout());
        assertEquals(15000, properties.getConnectionTimeout());
        assertNotNull(properties.getRetryPolicy());
    }

    @Test
    void testRetryPolicySetters() {
        ZooKeeperConfig.ZooKeeperProperties.RetryPolicy retryPolicy = 
            new ZooKeeperConfig.ZooKeeperProperties.RetryPolicy();
        
        retryPolicy.setBaseSleepTime(2000);
        retryPolicy.setMaxRetries(5);
        
        assertEquals(2000, retryPolicy.getBaseSleepTime());
        assertEquals(5, retryPolicy.getMaxRetries());
    }

    @Test
    void testZooKeeperPropertiesSetters() {
        ZooKeeperConfig.ZooKeeperProperties properties = 
            new ZooKeeperConfig.ZooKeeperProperties();
        
        properties.setConnectString("zk1:2181,zk2:2181");
        properties.setSessionTimeout(45000);
        properties.setConnectionTimeout(20000);
        
        assertEquals("zk1:2181,zk2:2181", properties.getConnectString());
        assertEquals(45000, properties.getSessionTimeout());
        assertEquals(20000, properties.getConnectionTimeout());
    }
}