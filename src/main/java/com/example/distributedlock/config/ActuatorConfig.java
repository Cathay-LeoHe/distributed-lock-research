package com.example.distributedlock.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.distributedlock.health.DistributedLockSystemHealthIndicator;
import com.example.distributedlock.health.RedisHealthIndicator;
import com.example.distributedlock.health.ZooKeeperHealthIndicator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Actuator 配置類別
 * 配置健康檢查、資訊端點和自定義監控指標
 */
@Configuration
@EnableConfigurationProperties({DistributedLockProperties.class, ApplicationProperties.class})
public class ActuatorConfig {

    private final DistributedLockProperties lockProperties;
    private final ApplicationProperties appProperties;

    public ActuatorConfig(DistributedLockProperties lockProperties, ApplicationProperties appProperties) {
        this.lockProperties = lockProperties;
        this.appProperties = appProperties;
    }

    /**
     * 配置分散式鎖健康檢查組合
     */
    @Bean("distributedLockHealth")
    public HealthContributor distributedLockHealthContributor(
            @Autowired(required = false) RedisHealthIndicator redisHealthIndicator,
            @Autowired(required = false) ZooKeeperHealthIndicator zooKeeperHealthIndicator,
            DistributedLockSystemHealthIndicator systemHealthIndicator) {
        
        Map<String, HealthContributor> contributors = new HashMap<>();
        
        // 添加系統整體健康檢查
        contributors.put("system", systemHealthIndicator);
        
        // 根據配置的提供者添加相應的健康檢查
        String provider = lockProperties.getProvider();
        if ("redis".equalsIgnoreCase(provider) && redisHealthIndicator != null) {
            contributors.put("redis", redisHealthIndicator);
        } else if ("zookeeper".equalsIgnoreCase(provider) && zooKeeperHealthIndicator != null) {
            contributors.put("zookeeper", zooKeeperHealthIndicator);
        } else {
            // 如果配置不明確，添加所有可用的健康檢查
            if (redisHealthIndicator != null) {
                contributors.put("redis", redisHealthIndicator);
            }
            if (zooKeeperHealthIndicator != null) {
                contributors.put("zookeeper", zooKeeperHealthIndicator);
            }
        }
        
        return CompositeHealthContributor.fromMap(contributors);
    }

    /**
     * 自定義應用程式資訊貢獻者
     */
    @Bean
    public InfoContributor customInfoContributor() {
        return builder -> {
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("name", appProperties.getName());
            appInfo.put("version", appProperties.getVersion());
            appInfo.put("description", appProperties.getDescription());
            appInfo.put("startupTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            Map<String, Object> lockInfo = new HashMap<>();
            lockInfo.put("provider", lockProperties.getProvider());
            lockInfo.put("defaultWaitTime", lockProperties.getLock().getDefaultWaitTime());
            lockInfo.put("defaultLeaseTime", lockProperties.getLock().getDefaultLeaseTime());
            lockInfo.put("maxWaitTime", lockProperties.getLock().getMaxWaitTime());
            lockInfo.put("maxLeaseTime", lockProperties.getLock().getMaxLeaseTime());
            
            // Redis 配置資訊
            if ("redis".equalsIgnoreCase(lockProperties.getProvider())) {
                Map<String, Object> redisInfo = new HashMap<>();
                redisInfo.put("host", lockProperties.getRedis().getHost());
                redisInfo.put("port", lockProperties.getRedis().getPort());
                redisInfo.put("database", lockProperties.getRedis().getDatabase());
                redisInfo.put("timeout", lockProperties.getRedis().getTimeout());
                redisInfo.put("retryAttempts", lockProperties.getRedis().getRetryAttempts());
                lockInfo.put("redis", redisInfo);
            }
            
            // ZooKeeper 配置資訊
            if ("zookeeper".equalsIgnoreCase(lockProperties.getProvider())) {
                Map<String, Object> zkInfo = new HashMap<>();
                zkInfo.put("connectString", lockProperties.getZookeeper().getConnectString());
                zkInfo.put("namespace", lockProperties.getZookeeper().getNamespace());
                zkInfo.put("sessionTimeout", lockProperties.getZookeeper().getSessionTimeout());
                zkInfo.put("connectionTimeout", lockProperties.getZookeeper().getConnectionTimeout());
                lockInfo.put("zookeeper", zkInfo);
            }
            
            builder.withDetail("application", appInfo);
            builder.withDetail("distributedLock", lockInfo);
        };
    }

    /**
     * 自定義系統資訊端點
     */
    @Endpoint(id = "system-info")
    @ConditionalOnAvailableEndpoint
    public static class SystemInfoEndpoint {

        @ReadOperation
        public Map<String, Object> systemInfo() {
            Map<String, Object> info = new HashMap<>();
            
            // JVM 資訊
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvmInfo = new HashMap<>();
            jvmInfo.put("availableProcessors", runtime.availableProcessors());
            jvmInfo.put("freeMemory", runtime.freeMemory());
            jvmInfo.put("totalMemory", runtime.totalMemory());
            jvmInfo.put("maxMemory", runtime.maxMemory());
            jvmInfo.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            
            // 系統屬性
            Map<String, Object> systemProps = new HashMap<>();
            systemProps.put("javaVersion", System.getProperty("java.version"));
            systemProps.put("javaVendor", System.getProperty("java.vendor"));
            systemProps.put("osName", System.getProperty("os.name"));
            systemProps.put("osVersion", System.getProperty("os.version"));
            systemProps.put("osArch", System.getProperty("os.arch"));
            
            info.put("jvm", jvmInfo);
            info.put("system", systemProps);
            info.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return info;
        }
    }

    @Bean
    public SystemInfoEndpoint systemInfoEndpoint() {
        return new SystemInfoEndpoint();
    }
}