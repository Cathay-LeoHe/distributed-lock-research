package com.example.distributedlock.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Redis 配置類別
 * 配置 Redisson 客戶端連接和相關設定
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 Redisson 客戶端
     * 
     * @param distributedLockProperties 分散式鎖配置屬性
     * @return RedissonClient 實例
     */
    @Bean
    @Primary
    public RedissonClient redissonClient(DistributedLockProperties distributedLockProperties) {
        DistributedLockProperties.RedisProperties redisProperties = distributedLockProperties.getRedis();
        Config config = new Config();
        
        // 配置單節點模式
        String address = String.format("redis://%s:%d", redisProperties.getHost(), redisProperties.getPort());
        var singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase())
                .setTimeout(redisProperties.getTimeout())
                .setRetryAttempts(redisProperties.getRetryAttempts())
                .setRetryInterval(redisProperties.getRetryInterval())
                .setConnectionPoolSize(redisProperties.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(redisProperties.getConnectionMinimumIdleSize())
                .setIdleConnectionTimeout(redisProperties.getIdleConnectionTimeout())
                .setConnectTimeout(redisProperties.getConnectTimeout())
                .setPingConnectionInterval(redisProperties.getPingConnectionInterval())
                .setKeepAlive(redisProperties.isKeepAlive());
        
        // 只有在密碼不為空時才設置密碼
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().trim().isEmpty()) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }
}