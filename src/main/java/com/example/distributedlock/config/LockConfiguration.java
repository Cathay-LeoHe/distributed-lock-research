package com.example.distributedlock.config;

import com.example.distributedlock.factory.DistributedLockFactory;
import com.example.distributedlock.lock.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 分散式鎖配置類別
 * 負責配置和管理分散式鎖的創建和切換
 */
@Configuration
@EnableConfigurationProperties(DistributedLockProperties.class)
public class LockConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LockConfiguration.class);

    /**
     * 創建主要的分散式鎖 Bean
     * 通過工廠模式動態選擇鎖實作
     * 
     * @param lockFactory 分散式鎖工廠
     * @return 分散式鎖實例
     */
    @Bean
    @Primary
    public DistributedLock distributedLock(DistributedLockFactory lockFactory) {
        logger.info("配置主要分散式鎖 Bean");
        return lockFactory.getDistributedLock();
    }

    /**
     * 創建鎖管理器 Bean
     * 提供鎖提供者切換和管理功能
     * 
     * @param lockFactory 分散式鎖工廠
     * @return 鎖管理器實例
     */
    @Bean
    public LockManager lockManager(DistributedLockFactory lockFactory) {
        logger.info("配置鎖管理器 Bean");
        return new LockManager(lockFactory);
    }

    /**
     * 鎖管理器類別
     * 提供鎖提供者的動態切換和管理功能
     */
    public static class LockManager {

        private static final Logger logger = LoggerFactory.getLogger(LockManager.class);

        private final DistributedLockFactory lockFactory;

        public LockManager(DistributedLockFactory lockFactory) {
            this.lockFactory = lockFactory;
        }

        /**
         * 切換鎖提供者
         * 
         * @param provider 新的鎖提供者（redis 或 zookeeper）
         * @return 切換是否成功
         */
        public boolean switchProvider(String provider) {
            try {
                logger.info("請求切換鎖提供者到: {}", provider);
                
                if (!isValidProvider(provider)) {
                    logger.error("無效的鎖提供者: {}", provider);
                    return false;
                }

                if (!lockFactory.isProviderAvailable(provider)) {
                    logger.error("鎖提供者不可用: {}", provider);
                    return false;
                }

                lockFactory.switchLockProvider(provider);
                logger.info("成功切換鎖提供者到: {}", provider);
                return true;

            } catch (Exception e) {
                logger.error("切換鎖提供者失敗: {}", provider, e);
                return false;
            }
        }

        /**
         * 獲取當前鎖提供者
         * 
         * @return 當前鎖提供者
         */
        public String getCurrentProvider() {
            return lockFactory.getCurrentProvider();
        }

        /**
         * 檢查鎖提供者是否可用
         * 
         * @param provider 鎖提供者
         * @return 是否可用
         */
        public boolean isProviderAvailable(String provider) {
            return lockFactory.isProviderAvailable(provider);
        }

        /**
         * 獲取鎖統計資訊
         * 
         * @return 鎖統計資訊
         */
        public DistributedLockFactory.LockStatistics getLockStatistics() {
            return lockFactory.getLockStatistics();
        }

        /**
         * 獲取支持的鎖提供者列表
         * 
         * @return 支持的鎖提供者陣列
         */
        public String[] getSupportedProviders() {
            return new String[]{"redis", "zookeeper"};
        }

        /**
         * 檢查鎖提供者是否有效
         * 
         * @param provider 鎖提供者
         * @return 是否有效
         */
        private boolean isValidProvider(String provider) {
            if (provider == null || provider.trim().isEmpty()) {
                return false;
            }

            String normalizedProvider = provider.toLowerCase().trim();
            for (String supportedProvider : getSupportedProviders()) {
                if (supportedProvider.equals(normalizedProvider)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 獲取鎖提供者狀態資訊
         * 
         * @return 鎖提供者狀態
         */
        public LockProviderStatus getProviderStatus() {
            String currentProvider = getCurrentProvider();
            DistributedLockFactory.LockStatistics stats = getLockStatistics();
            
            boolean redisAvailable = isProviderAvailable("redis");
            boolean zookeeperAvailable = isProviderAvailable("zookeeper");

            return new LockProviderStatus(
                currentProvider,
                stats.getLockCount(),
                redisAvailable,
                zookeeperAvailable
            );
        }
    }

    /**
     * 鎖提供者狀態資訊類別
     */
    public static class LockProviderStatus {
        private final String currentProvider;
        private final int activeLocks;
        private final boolean redisAvailable;
        private final boolean zookeeperAvailable;

        public LockProviderStatus(String currentProvider, int activeLocks, 
                                boolean redisAvailable, boolean zookeeperAvailable) {
            this.currentProvider = currentProvider;
            this.activeLocks = activeLocks;
            this.redisAvailable = redisAvailable;
            this.zookeeperAvailable = zookeeperAvailable;
        }

        public String getCurrentProvider() {
            return currentProvider;
        }

        public int getActiveLocks() {
            return activeLocks;
        }

        public boolean isRedisAvailable() {
            return redisAvailable;
        }

        public boolean isZookeeperAvailable() {
            return zookeeperAvailable;
        }

        @Override
        public String toString() {
            return String.format(
                "LockProviderStatus{currentProvider='%s', activeLocks=%d, redisAvailable=%s, zookeeperAvailable=%s}",
                currentProvider, activeLocks, redisAvailable, zookeeperAvailable
            );
        }
    }
}