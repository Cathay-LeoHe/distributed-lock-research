package com.example.distributedlock.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置資訊端點
 * 提供當前應用程式配置的查詢功能
 */
@RestController
@RequestMapping("/actuator/config-info")
public class ConfigurationInfoEndpoint {

    private final DistributedLockProperties distributedLockProperties;
    private final ApplicationProperties applicationProperties;

    @Autowired
    public ConfigurationInfoEndpoint(
            DistributedLockProperties distributedLockProperties,
            ApplicationProperties applicationProperties) {
        this.distributedLockProperties = distributedLockProperties;
        this.applicationProperties = applicationProperties;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> configInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // 應用程式資訊
        Map<String, Object> appInfo = new HashMap<>();
        appInfo.put("name", applicationProperties.getName());
        appInfo.put("version", applicationProperties.getVersion());
        appInfo.put("description", applicationProperties.getDescription());
        info.put("application", appInfo);
        
        // 分散式鎖配置
        Map<String, Object> lockInfo = new HashMap<>();
        lockInfo.put("provider", distributedLockProperties.getProvider());
        lockInfo.put("defaultWaitTime", distributedLockProperties.getLock().getDefaultWaitTime());
        lockInfo.put("defaultLeaseTime", distributedLockProperties.getLock().getDefaultLeaseTime());
        lockInfo.put("maxWaitTime", distributedLockProperties.getLock().getMaxWaitTime());
        lockInfo.put("maxLeaseTime", distributedLockProperties.getLock().getMaxLeaseTime());
        info.put("distributedLock", lockInfo);
        
        // Redis 配置 (隱藏敏感資訊)
        Map<String, Object> redisInfo = new HashMap<>();
        var redisProps = distributedLockProperties.getRedis();
        redisInfo.put("host", redisProps.getHost());
        redisInfo.put("port", redisProps.getPort());
        redisInfo.put("database", redisProps.getDatabase());
        redisInfo.put("timeout", redisProps.getTimeout());
        redisInfo.put("connectionPoolSize", redisProps.getConnectionPoolSize());
        redisInfo.put("clusterEnabled", redisProps.getCluster().isEnabled());
        if (redisProps.getCluster().isEnabled()) {
            redisInfo.put("clusterNodes", redisProps.getCluster().getNodes());
        }
        info.put("redis", redisInfo);
        
        // ZooKeeper 配置
        Map<String, Object> zkInfo = new HashMap<>();
        var zkProps = distributedLockProperties.getZookeeper();
        zkInfo.put("connectString", zkProps.getConnectString());
        zkInfo.put("namespace", zkProps.getNamespace());
        zkInfo.put("sessionTimeout", zkProps.getSessionTimeout());
        zkInfo.put("connectionTimeout", zkProps.getConnectionTimeout());
        zkInfo.put("retryPolicy", Map.of(
            "baseSleepTime", zkProps.getRetryPolicy().getBaseSleepTime(),
            "maxRetries", zkProps.getRetryPolicy().getMaxRetries(),
            "maxSleepTime", zkProps.getRetryPolicy().getMaxSleepTime()
        ));
        info.put("zookeeper", zkInfo);
        
        return ResponseEntity.ok(info);
    }
}