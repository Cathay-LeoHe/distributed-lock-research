package com.example.distributedlock.health;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Redis 健康檢查組件
 * 檢查 Redis 連接狀態和可用性
 */
@Component
@ConditionalOnProperty(name = "distributed-lock.provider", havingValue = "redis")
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(RedisHealthIndicator.class);
    
    private final RedissonClient redissonClient;

    public RedisHealthIndicator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Health health() {
        try {
            return checkRedisConnection();
        } catch (Exception e) {
            logger.error("Redis 健康檢查失敗", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("exception", e.getClass().getSimpleName())
                .build();
        }
    }

    private Health checkRedisConnection() {
        if (redissonClient == null) {
            return Health.down()
                .withDetail("reason", "RedissonClient 未初始化")
                .build();
        }

        // 檢查 Redisson 客戶端是否已關閉
        if (redissonClient.isShutdown()) {
            logger.warn("Redis health check failed: Client is shutdown");
            return Health.down()
                .withDetail("reason", "Client is shutdown")
                .withDetail("message", "Redisson client has been shutdown")
                .build();
        }

        try {
            Instant startTime = Instant.now();
            
            // 嘗試執行簡單的 ping 操作來檢查連接
            String testKey = "health:check:" + System.currentTimeMillis();
            String testValue = "ping";
            
            // 設置一個測試值，並設定 TTL 避免垃圾數據
            redissonClient.getBucket(testKey).set(testValue, 30, TimeUnit.SECONDS);
            
            // 讀取測試值
            Object retrievedValue = redissonClient.getBucket(testKey).get();
            
            // 刪除測試值
            redissonClient.getBucket(testKey).delete();
            
            Duration responseTime = Duration.between(startTime, Instant.now());
            
            // 驗證讀取的值是否正確
            if (testValue.equals(retrievedValue)) {
                logger.debug("Redis health check passed");
                return Health.up()
                    .withDetail("status", "Connected")
                    .withDetail("message", "Read/Write operation successful")
                    .withDetail("responseTime", responseTime.toMillis() + "ms")
                    .withDetail("testKey", testKey)
                    .withDetail("clientType", redissonClient.getClass().getSimpleName())
                    .build();
            } else {
                logger.warn("Redis health check failed: Read/Write operation failed");
                return Health.down()
                    .withDetail("reason", "Connection issue")
                    .withDetail("message", "Read/Write operation failed")
                    .withDetail("expected", testValue)
                    .withDetail("actual", retrievedValue)
                    .withDetail("responseTime", responseTime.toMillis() + "ms")
                    .build();
            }
            
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            return Health.down()
                .withDetail("reason", "Connection failed")
                .withDetail("error", e.getMessage())
                .withDetail("exception", e.getClass().getSimpleName())
                .build();
        }
    }
}