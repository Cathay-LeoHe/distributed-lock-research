package com.example.distributedlock.factory;

import com.example.distributedlock.config.DistributedLockProperties;
import com.example.distributedlock.lock.DistributedLock;
import com.example.distributedlock.lock.RedisDistributedLock;
import com.example.distributedlock.lock.ZooKeeperDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 分散式鎖工廠類別
 * 根據配置動態選擇和創建適當的分散式鎖實作
 */
@Component
public class DistributedLockFactory {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockFactory.class);

    private final DistributedLockProperties lockProperties;
    private final ApplicationContext applicationContext;
    
    private DistributedLock distributedLock;

    @Autowired
    public DistributedLockFactory(DistributedLockProperties lockProperties, 
                                ApplicationContext applicationContext) {
        this.lockProperties = lockProperties;
        this.applicationContext = applicationContext;
    }

    /**
     * 初始化分散式鎖實例
     */
    @PostConstruct
    public void initializeLock() {
        String provider = lockProperties.getProvider().toLowerCase().trim();
        logger.info("初始化分散式鎖，提供者: {}", provider);

        try {
            switch (provider) {
                case "redis":
                    this.distributedLock = createRedisLock();
                    logger.info("成功初始化 Redis 分散式鎖");
                    break;
                case "zookeeper":
                    this.distributedLock = createZooKeeperLock();
                    logger.info("成功初始化 ZooKeeper 分散式鎖");
                    break;
                default:
                    logger.error("不支持的鎖提供者: {}，將使用預設的 Redis 鎖", provider);
                    this.distributedLock = createRedisLock();
                    break;
            }
        } catch (Exception e) {
            logger.error("初始化分散式鎖失敗，提供者: {}", provider, e);
            throw new RuntimeException("分散式鎖初始化失敗", e);
        }
    }

    /**
     * 獲取分散式鎖實例
     * 
     * @return 分散式鎖實例
     */
    public DistributedLock getDistributedLock() {
        if (distributedLock == null) {
            throw new IllegalStateException("分散式鎖尚未初始化");
        }
        return distributedLock;
    }

    /**
     * 動態切換鎖提供者
     * 
     * @param provider 新的鎖提供者（redis 或 zookeeper）
     */
    public synchronized void switchLockProvider(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("鎖提供者不能為空");
        }

        String newProvider = provider.toLowerCase().trim();
        String currentProvider = lockProperties.getProvider().toLowerCase().trim();

        if (newProvider.equals(currentProvider)) {
            logger.info("鎖提供者未改變，當前提供者: {}", currentProvider);
            return;
        }

        logger.info("切換鎖提供者從 {} 到 {}", currentProvider, newProvider);

        try {
            // 清理當前鎖資源
            cleanupCurrentLock();

            // 更新配置
            lockProperties.setProvider(newProvider);

            // 重新初始化鎖
            initializeLock();

            logger.info("成功切換鎖提供者到: {}", newProvider);
        } catch (Exception e) {
            logger.error("切換鎖提供者失敗，從 {} 到 {}", currentProvider, newProvider, e);
            throw new RuntimeException("鎖提供者切換失敗", e);
        }
    }

    /**
     * 獲取當前鎖提供者類型
     * 
     * @return 當前鎖提供者類型
     */
    public String getCurrentProvider() {
        return lockProperties.getProvider();
    }

    /**
     * 檢查指定的鎖提供者是否可用
     * 
     * @param provider 鎖提供者類型
     * @return 是否可用
     */
    public boolean isProviderAvailable(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            return false;
        }

        String providerType = provider.toLowerCase().trim();
        
        try {
            switch (providerType) {
                case "redis":
                    return applicationContext.getBeansOfType(RedisDistributedLock.class).size() > 0;
                case "zookeeper":
                    return applicationContext.getBeansOfType(ZooKeeperDistributedLock.class).size() > 0;
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.warn("檢查鎖提供者可用性時發生異常: {}", provider, e);
            return false;
        }
    }

    /**
     * 創建 Redis 分散式鎖
     * 
     * @return Redis 分散式鎖實例
     */
    private DistributedLock createRedisLock() {
        try {
            RedisDistributedLock redisLock = applicationContext.getBean(RedisDistributedLock.class);
            logger.debug("成功獲取 Redis 分散式鎖 Bean");
            return redisLock;
        } catch (Exception e) {
            logger.error("創建 Redis 分散式鎖失敗", e);
            throw new RuntimeException("Redis 分散式鎖創建失敗", e);
        }
    }

    /**
     * 創建 ZooKeeper 分散式鎖
     * 
     * @return ZooKeeper 分散式鎖實例
     */
    private DistributedLock createZooKeeperLock() {
        try {
            ZooKeeperDistributedLock zkLock = applicationContext.getBean(ZooKeeperDistributedLock.class);
            logger.debug("成功獲取 ZooKeeper 分散式鎖 Bean");
            return zkLock;
        } catch (Exception e) {
            logger.error("創建 ZooKeeper 分散式鎖失敗", e);
            throw new RuntimeException("ZooKeeper 分散式鎖創建失敗", e);
        }
    }

    /**
     * 清理當前鎖資源
     */
    private void cleanupCurrentLock() {
        if (distributedLock == null) {
            return;
        }

        try {
            if (distributedLock instanceof RedisDistributedLock) {
                RedisDistributedLock redisLock = (RedisDistributedLock) distributedLock;
                redisLock.clearHeldLocks();
                logger.debug("清理 Redis 鎖資源");
            } else if (distributedLock instanceof ZooKeeperDistributedLock) {
                ZooKeeperDistributedLock zkLock = (ZooKeeperDistributedLock) distributedLock;
                zkLock.cleanupAllLocks();
                logger.debug("清理 ZooKeeper 鎖資源");
            }
        } catch (Exception e) {
            logger.warn("清理鎖資源時發生異常", e);
        }
    }

    /**
     * 獲取鎖統計資訊
     * 
     * @return 鎖統計資訊
     */
    public LockStatistics getLockStatistics() {
        if (distributedLock == null) {
            return new LockStatistics("unknown", 0);
        }

        String provider = getCurrentProvider();
        int lockCount = 0;

        try {
            if (distributedLock instanceof RedisDistributedLock) {
                RedisDistributedLock redisLock = (RedisDistributedLock) distributedLock;
                lockCount = redisLock.getHeldLocksCount();
            } else if (distributedLock instanceof ZooKeeperDistributedLock) {
                ZooKeeperDistributedLock zkLock = (ZooKeeperDistributedLock) distributedLock;
                lockCount = zkLock.getCachedLockCount();
            }
        } catch (Exception e) {
            logger.warn("獲取鎖統計資訊時發生異常", e);
        }

        return new LockStatistics(provider, lockCount);
    }

    /**
     * 鎖統計資訊類別
     */
    public static class LockStatistics {
        private final String provider;
        private final int lockCount;

        public LockStatistics(String provider, int lockCount) {
            this.provider = provider;
            this.lockCount = lockCount;
        }

        public String getProvider() {
            return provider;
        }

        public int getLockCount() {
            return lockCount;
        }

        @Override
        public String toString() {
            return String.format("LockStatistics{provider='%s', lockCount=%d}", provider, lockCount);
        }
    }
}