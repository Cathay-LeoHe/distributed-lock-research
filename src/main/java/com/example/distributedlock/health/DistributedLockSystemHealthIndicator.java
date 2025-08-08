package com.example.distributedlock.health;

import com.example.distributedlock.config.DistributedLockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 分散式鎖系統整體健康檢查指標
 * 提供分散式鎖系統的整體健康狀態
 */
@Component
public class DistributedLockSystemHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockSystemHealthIndicator.class);

    private final DistributedLockProperties lockProperties;
    private final Optional<RedisHealthIndicator> redisHealthIndicator;
    private final Optional<ZooKeeperHealthIndicator> zooKeeperHealthIndicator;

    public DistributedLockSystemHealthIndicator(
            DistributedLockProperties lockProperties,
            @Autowired(required = false) RedisHealthIndicator redisHealthIndicator,
            @Autowired(required = false) ZooKeeperHealthIndicator zooKeeperHealthIndicator) {
        this.lockProperties = lockProperties;
        this.redisHealthIndicator = Optional.ofNullable(redisHealthIndicator);
        this.zooKeeperHealthIndicator = Optional.ofNullable(zooKeeperHealthIndicator);
    }

    @Override
    public Health health() {
        try {
            return checkDistributedLockSystemHealth();
        } catch (Exception e) {
            logger.error("分散式鎖系統健康檢查失敗", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("exception", e.getClass().getSimpleName())
                .withDetail("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
        }
    }

    private Health checkDistributedLockSystemHealth() {
        String provider = lockProperties.getProvider();
        Health.Builder healthBuilder = Health.up();
        
        Map<String, Object> details = new HashMap<>();
        details.put("provider", provider);
        details.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // 檢查配置的提供者健康狀態
        boolean isHealthy = true;
        String healthMessage = "系統運行正常";
        
        if ("redis".equalsIgnoreCase(provider)) {
            if (redisHealthIndicator.isPresent()) {
                Health redisHealth = redisHealthIndicator.get().health();
                isHealthy = redisHealth.getStatus().getCode().equals("UP");
                details.put("redis", redisHealth.getDetails());
                
                if (!isHealthy) {
                    healthMessage = "Redis 連接異常";
                    healthBuilder = Health.down();
                }
            } else {
                isHealthy = false;
                healthMessage = "Redis 健康檢查器未找到";
                healthBuilder = Health.down();
            }
            
        } else if ("zookeeper".equalsIgnoreCase(provider)) {
            if (zooKeeperHealthIndicator.isPresent()) {
                Health zkHealth = zooKeeperHealthIndicator.get().health();
                isHealthy = zkHealth.getStatus().getCode().equals("UP");
                details.put("zookeeper", zkHealth.getDetails());
                
                if (!isHealthy) {
                    healthMessage = "ZooKeeper 連接異常";
                    healthBuilder = Health.down();
                }
            } else {
                isHealthy = false;
                healthMessage = "ZooKeeper 健康檢查器未找到";
                healthBuilder = Health.down();
            }
            
        } else {
            // 未知的提供者配置
            isHealthy = false;
            healthMessage = "未知的分散式鎖提供者: " + provider;
            healthBuilder = Health.down();
        }
        
        // 添加配置資訊
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("defaultWaitTime", lockProperties.getLock().getDefaultWaitTime());
        configInfo.put("defaultLeaseTime", lockProperties.getLock().getDefaultLeaseTime());
        configInfo.put("maxWaitTime", lockProperties.getLock().getMaxWaitTime());
        configInfo.put("maxLeaseTime", lockProperties.getLock().getMaxLeaseTime());
        
        details.put("configuration", configInfo);
        details.put("message", healthMessage);
        details.put("healthy", isHealthy);
        
        return healthBuilder
            .withDetails(details)
            .build();
    }
}