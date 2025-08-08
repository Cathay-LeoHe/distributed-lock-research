package com.example.distributedlock.health;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ZooKeeper 健康檢查測試
 */
@ExtendWith(MockitoExtension.class)
class ZooKeeperHealthIndicatorTest {

    @Mock
    private CuratorFramework curatorFramework;

    @Mock
    private org.apache.curator.CuratorZookeeperClient zookeeperClient;

    @Mock
    private ZooKeeper zooKeeper;

    @Mock
    private ExistsBuilder existsBuilder;

    private ZooKeeperHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new ZooKeeperHealthIndicator(curatorFramework);
    }

    @Test
    void testHealthCheckWhenConnected() throws Exception {
        // 模擬連接狀態
        when(curatorFramework.getState()).thenReturn(CuratorFrameworkState.STARTED);
        when(curatorFramework.getZookeeperClient()).thenReturn(zookeeperClient);
        when(zookeeperClient.isConnected()).thenReturn(true);
        when(zookeeperClient.getCurrentConnectionString()).thenReturn("localhost:2181");
        when(zookeeperClient.getZooKeeper()).thenReturn(zooKeeper);
        when(zooKeeper.getSessionId()).thenReturn(0x123456789L);
        
        // 模擬操作測試
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/")).thenReturn(null);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(CuratorFrameworkState.STARTED.toString(), health.getDetails().get("frameworkState"));
        assertEquals(true, health.getDetails().get("isStarted"));
        assertEquals(true, health.getDetails().get("isConnected"));
        assertEquals("localhost:2181", health.getDetails().get("connectString"));
        assertEquals("0x123456789", health.getDetails().get("sessionId"));
        assertEquals("SUCCESS", health.getDetails().get("operationTest"));
    }

    @Test
    void testHealthCheckWhenDisconnected() throws Exception {
        // 模擬斷開連接狀態
        when(curatorFramework.getState()).thenReturn(CuratorFrameworkState.STARTED);
        when(curatorFramework.getZookeeperClient()).thenReturn(zookeeperClient);
        when(zookeeperClient.isConnected()).thenReturn(false);
        when(zookeeperClient.getCurrentConnectionString()).thenReturn("localhost:2181");
        when(zookeeperClient.getZooKeeper()).thenReturn(zooKeeper);
        when(zooKeeper.getSessionId()).thenReturn(0x123456789L);

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(CuratorFrameworkState.STARTED.toString(), health.getDetails().get("frameworkState"));
        assertEquals(true, health.getDetails().get("isStarted"));
        assertEquals(false, health.getDetails().get("isConnected"));
    }

    @Test
    void testHealthCheckWhenOperationFails() throws Exception {
        // 模擬連接狀態正常但操作失敗
        when(curatorFramework.getState()).thenReturn(CuratorFrameworkState.STARTED);
        when(curatorFramework.getZookeeperClient()).thenReturn(zookeeperClient);
        when(zookeeperClient.isConnected()).thenReturn(true);
        when(zookeeperClient.getCurrentConnectionString()).thenReturn("localhost:2181");
        when(zookeeperClient.getZooKeeper()).thenReturn(zooKeeper);
        when(zooKeeper.getSessionId()).thenReturn(0x123456789L);
        
        // 模擬操作失敗
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/")).thenThrow(new RuntimeException("操作失敗"));

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("FAILED", health.getDetails().get("operationTest"));
        assertEquals("操作失敗", health.getDetails().get("operationError"));
    }

    @Test
    void testHealthCheckWhenCuratorFrameworkIsNull() {
        ZooKeeperHealthIndicator nullHealthIndicator = new ZooKeeperHealthIndicator(null);

        Health health = nullHealthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("CuratorFramework 未初始化", health.getDetails().get("reason"));
    }

    @Test
    void testHealthCheckWhenExceptionThrown() {
        // 模擬拋出異常
        when(curatorFramework.getState()).thenThrow(new RuntimeException("連接異常"));

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("連接異常", health.getDetails().get("error"));
        assertEquals("RuntimeException", health.getDetails().get("exception"));
    }

    @Test
    void testHealthCheckWithUnknownConnectionString() throws Exception {
        // 模擬獲取連接字串失敗
        when(curatorFramework.getState()).thenReturn(CuratorFrameworkState.STARTED);
        when(curatorFramework.getZookeeperClient()).thenReturn(zookeeperClient);
        when(zookeeperClient.isConnected()).thenReturn(true);
        when(zookeeperClient.getCurrentConnectionString()).thenThrow(new RuntimeException("無法獲取連接字串"));
        when(zookeeperClient.getZooKeeper()).thenReturn(zooKeeper);
        when(zooKeeper.getSessionId()).thenReturn(0x123456789L);
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/")).thenReturn(null);

        Health health = healthIndicator.health();

        assertEquals("未知", health.getDetails().get("connectString"));
    }

    @Test
    void testHealthCheckWithUnknownSessionId() throws Exception {
        // 模擬獲取會話 ID 失敗
        when(curatorFramework.getState()).thenReturn(CuratorFrameworkState.STARTED);
        when(curatorFramework.getZookeeperClient()).thenReturn(zookeeperClient);
        when(zookeeperClient.isConnected()).thenReturn(true);
        when(zookeeperClient.getCurrentConnectionString()).thenReturn("localhost:2181");
        when(zookeeperClient.getZooKeeper()).thenThrow(new RuntimeException("無法獲取會話 ID"));
        when(curatorFramework.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/")).thenReturn(null);

        Health health = healthIndicator.health();

        assertEquals("未知", health.getDetails().get("sessionId"));
    }
}