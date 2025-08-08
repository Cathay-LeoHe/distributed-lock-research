package com.example.distributedlock.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * ZooKeeper 配置類別
 * 負責配置 Apache Curator 客戶端和連接管理
 */
@Configuration
public class ZooKeeperConfig {

    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperConfig.class);
    
    private CuratorFramework curatorFramework;

    @Bean
    @ConfigurationProperties(prefix = "distributed-lock.zookeeper")
    public ZooKeeperProperties zooKeeperProperties() {
        return new ZooKeeperProperties();
    }

    @Bean
    public CuratorFramework curatorFramework(ZooKeeperProperties properties) {
        logger.info("初始化 ZooKeeper Curator 客戶端，連接字串: {}", properties.getConnectString());
        
        // 創建重試策略
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(
            properties.getRetryPolicy().getBaseSleepTime(),
            properties.getRetryPolicy().getMaxRetries()
        );
        
        // 建立 Curator 客戶端
        this.curatorFramework = CuratorFrameworkFactory.builder()
            .connectString(properties.getConnectString())
            .sessionTimeoutMs(properties.getSessionTimeout())
            .connectionTimeoutMs(properties.getConnectionTimeout())
            .retryPolicy(retryPolicy)
            .build();
        
        // 啟動客戶端
        this.curatorFramework.start();
        
        try {
            // 等待連接建立
            boolean connected = this.curatorFramework.blockUntilConnected(
                properties.getConnectionTimeout(), 
                java.util.concurrent.TimeUnit.MILLISECONDS
            );
            
            if (connected) {
                logger.info("ZooKeeper 客戶端連接成功");
            } else {
                logger.warn("ZooKeeper 客戶端連接超時");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("等待 ZooKeeper 連接時被中斷", e);
            throw new RuntimeException("ZooKeeper 連接初始化失敗", e);
        }
        
        return this.curatorFramework;
    }

    @PreDestroy
    public void cleanup() {
        if (curatorFramework != null) {
            logger.info("關閉 ZooKeeper 客戶端連接");
            curatorFramework.close();
        }
    }

    /**
     * ZooKeeper 配置屬性類別
     */
    public static class ZooKeeperProperties {
        private String connectString = "localhost:2181";
        private int sessionTimeout = 60000;
        private int connectionTimeout = 15000;
        private RetryPolicy retryPolicy = new RetryPolicy();

        public String getConnectString() {
            return connectString;
        }

        public void setConnectString(String connectString) {
            this.connectString = connectString;
        }

        public int getSessionTimeout() {
            return sessionTimeout;
        }

        public void setSessionTimeout(int sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        public void setRetryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        public static class RetryPolicy {
            private int baseSleepTime = 1000;
            private int maxRetries = 3;

            public int getBaseSleepTime() {
                return baseSleepTime;
            }

            public void setBaseSleepTime(int baseSleepTime) {
                this.baseSleepTime = baseSleepTime;
            }

            public int getMaxRetries() {
                return maxRetries;
            }

            public void setMaxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
            }
        }
    }
}