package com.example.distributedlock.health;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ZooKeeper 健康檢查組件
 * 監控 ZooKeeper 連接狀態
 */
@Component
@ConditionalOnProperty(name = "distributed-lock.provider", havingValue = "zookeeper")
public class ZooKeeperHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperHealthIndicator.class);
    
    private final CuratorFramework curatorFramework;

    public ZooKeeperHealthIndicator(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
    }

    @Override
    public Health health() {
        try {
            return checkZooKeeperConnection();
        } catch (Exception e) {
            logger.error("ZooKeeper 健康檢查失敗", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("exception", e.getClass().getSimpleName())
                .build();
        }
    }

    private Health checkZooKeeperConnection() {
        if (curatorFramework == null) {
            return Health.down()
                .withDetail("reason", "CuratorFramework 未初始化")
                .build();
        }

        // 檢查 Curator 框架狀態
        boolean isStarted = curatorFramework.getState() == org.apache.curator.framework.imps.CuratorFrameworkState.STARTED;
        boolean isConnected = false;
        
        if (isStarted) {
            try {
                // 嘗試獲取 ZooKeeper 客戶端來檢查連接狀態
                isConnected = curatorFramework.getZookeeperClient().isConnected();
            } catch (Exception e) {
                logger.warn("檢查 ZooKeeper 連接狀態時發生異常", e);
            }
        }

        Health.Builder healthBuilder = (isStarted && isConnected) ? Health.up() : Health.down();
        
        healthBuilder
            .withDetail("frameworkState", curatorFramework.getState().toString())
            .withDetail("isStarted", isStarted)
            .withDetail("isConnected", isConnected)
            .withDetail("connectString", getConnectString())
            .withDetail("sessionId", getSessionId());

        // 嘗試執行簡單的操作來驗證連接
        if (isStarted && isConnected) {
            try {
                // 檢查根節點是否存在
                curatorFramework.checkExists().forPath("/");
                healthBuilder.withDetail("operationTest", "SUCCESS");
                logger.debug("ZooKeeper 連接健康檢查通過");
            } catch (Exception e) {
                logger.warn("ZooKeeper 操作測試失敗", e);
                healthBuilder = Health.down();
                healthBuilder
                    .withDetail("operationTest", "FAILED")
                    .withDetail("operationError", e.getMessage());
            }
        }

        return healthBuilder.build();
    }

    private String getConnectString() {
        try {
            return curatorFramework.getZookeeperClient().getCurrentConnectionString();
        } catch (Exception e) {
            return "未知";
        }
    }

    private String getSessionId() {
        try {
            long sessionId = curatorFramework.getZookeeperClient().getZooKeeper().getSessionId();
            return String.format("0x%x", sessionId);
        } catch (Exception e) {
            return "未知";
        }
    }
}