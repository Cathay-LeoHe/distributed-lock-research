package com.example.distributedlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 分散式鎖配置屬性
 * 用於綁定 application.yml 中的分散式鎖相關配置
 */
@ConfigurationProperties(prefix = "distributed-lock")
@Validated
public class DistributedLockProperties {

    /**
     * 鎖提供者類型：redis 或 zookeeper
     */
    @NotBlank(message = "Lock provider cannot be blank")
    @Pattern(regexp = "^(redis|zookeeper)$", message = "Lock provider must be either 'redis' or 'zookeeper'")
    private String provider = "redis";

    /**
     * 鎖相關通用配置
     */
    @Valid
    private LockProperties lock = new LockProperties();

    /**
     * Redis 配置
     */
    @Valid
    private RedisProperties redis = new RedisProperties();

    /**
     * ZooKeeper 配置
     */
    @Valid
    private ZooKeeperProperties zookeeper = new ZooKeeperProperties();

    // Getters and Setters
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LockProperties getLock() {
        return lock;
    }

    public void setLock(LockProperties lock) {
        this.lock = lock;
    }

    public RedisProperties getRedis() {
        return redis;
    }

    public void setRedis(RedisProperties redis) {
        this.redis = redis;
    }

    public ZooKeeperProperties getZookeeper() {
        return zookeeper;
    }

    public void setZookeeper(ZooKeeperProperties zookeeper) {
        this.zookeeper = zookeeper;
    }

    /**
     * 鎖相關通用配置屬性
     */
    public static class LockProperties {
        @Positive(message = "Default wait time must be positive")
        private long defaultWaitTime = 10000; // 預設等待時間 (毫秒)

        @Positive(message = "Default lease time must be positive")
        private long defaultLeaseTime = 30000; // 預設租約時間 (毫秒)

        @Positive(message = "Max wait time must be positive")
        private long maxWaitTime = 60000; // 最大等待時間 (毫秒)

        @Positive(message = "Max lease time must be positive")
        private long maxLeaseTime = 300000; // 最大租約時間 (毫秒)

        // Getters and Setters
        public long getDefaultWaitTime() {
            return defaultWaitTime;
        }

        public void setDefaultWaitTime(long defaultWaitTime) {
            this.defaultWaitTime = defaultWaitTime;
        }

        public long getDefaultLeaseTime() {
            return defaultLeaseTime;
        }

        public void setDefaultLeaseTime(long defaultLeaseTime) {
            this.defaultLeaseTime = defaultLeaseTime;
        }

        public long getMaxWaitTime() {
            return maxWaitTime;
        }

        public void setMaxWaitTime(long maxWaitTime) {
            this.maxWaitTime = maxWaitTime;
        }

        public long getMaxLeaseTime() {
            return maxLeaseTime;
        }

        public void setMaxLeaseTime(long maxLeaseTime) {
            this.maxLeaseTime = maxLeaseTime;
        }
    }

    /**
     * Redis 配置屬性
     */
    public static class RedisProperties {
        @NotBlank(message = "Redis host cannot be blank")
        private String host = "localhost";

        @Positive(message = "Redis port must be positive")
        private int port = 6379;

        private String password = "";

        @PositiveOrZero(message = "Redis database must be non-negative")
        private int database = 0;

        @Positive(message = "Redis timeout must be positive")
        private int timeout = 3000;

        @Positive(message = "Redis retry attempts must be positive")
        private int retryAttempts = 3;

        @Positive(message = "Redis retry interval must be positive")
        private int retryInterval = 1500;

        @Positive(message = "Redis connection pool size must be positive")
        private int connectionPoolSize = 64;

        @PositiveOrZero(message = "Redis connection minimum idle size must be non-negative")
        private int connectionMinimumIdleSize = 10;

        @Positive(message = "Redis idle connection timeout must be positive")
        private int idleConnectionTimeout = 10000;

        @Positive(message = "Redis connect timeout must be positive")
        private int connectTimeout = 10000;

        @Positive(message = "Redis ping connection interval must be positive")
        private int pingConnectionInterval = 30000;

        private boolean keepAlive = true;

        /**
         * Redis 集群配置
         */
        @Valid
        private ClusterProperties cluster = new ClusterProperties();

        // Getters and Setters
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public int getRetryInterval() {
            return retryInterval;
        }

        public void setRetryInterval(int retryInterval) {
            this.retryInterval = retryInterval;
        }

        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }

        public void setConnectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
        }

        public int getConnectionMinimumIdleSize() {
            return connectionMinimumIdleSize;
        }

        public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) {
            this.connectionMinimumIdleSize = connectionMinimumIdleSize;
        }

        public int getIdleConnectionTimeout() {
            return idleConnectionTimeout;
        }

        public void setIdleConnectionTimeout(int idleConnectionTimeout) {
            this.idleConnectionTimeout = idleConnectionTimeout;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getPingConnectionInterval() {
            return pingConnectionInterval;
        }

        public void setPingConnectionInterval(int pingConnectionInterval) {
            this.pingConnectionInterval = pingConnectionInterval;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }

        public ClusterProperties getCluster() {
            return cluster;
        }

        public void setCluster(ClusterProperties cluster) {
            this.cluster = cluster;
        }

        /**
         * Redis 集群配置屬性
         */
        public static class ClusterProperties {
            private boolean enabled = false;

            @NotBlank(message = "Redis cluster nodes cannot be blank when cluster is enabled")
            private String nodes = "localhost:6379";

            @Positive(message = "Redis cluster scan interval must be positive")
            private int scanInterval = 5000;

            @Pattern(regexp = "^(SLAVE|MASTER|MASTER_SLAVE)$", 
                    message = "Redis read mode must be SLAVE, MASTER, or MASTER_SLAVE")
            private String readMode = "SLAVE";

            @Pattern(regexp = "^(SLAVE|MASTER)$", 
                    message = "Redis subscription mode must be SLAVE or MASTER")
            private String subscriptionMode = "MASTER";

            // Getters and Setters
            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getNodes() {
                return nodes;
            }

            public void setNodes(String nodes) {
                this.nodes = nodes;
            }

            public int getScanInterval() {
                return scanInterval;
            }

            public void setScanInterval(int scanInterval) {
                this.scanInterval = scanInterval;
            }

            public String getReadMode() {
                return readMode;
            }

            public void setReadMode(String readMode) {
                this.readMode = readMode;
            }

            public String getSubscriptionMode() {
                return subscriptionMode;
            }

            public void setSubscriptionMode(String subscriptionMode) {
                this.subscriptionMode = subscriptionMode;
            }
        }
    }

    /**
     * ZooKeeper 配置屬性
     */
    public static class ZooKeeperProperties {
        @NotBlank(message = "ZooKeeper connect string cannot be blank")
        private String connectString = "localhost:2181";

        private String namespace = "distributed-locks";

        @Positive(message = "ZooKeeper session timeout must be positive")
        private int sessionTimeout = 60000;

        @Positive(message = "ZooKeeper connection timeout must be positive")
        private int connectionTimeout = 15000;

        @Valid
        private RetryPolicy retryPolicy = new RetryPolicy();

        // Getters and Setters
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

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        /**
         * ZooKeeper 重試策略配置
         */
        public static class RetryPolicy {
            @Positive(message = "ZooKeeper base sleep time must be positive")
            private int baseSleepTime = 1000;

            @PositiveOrZero(message = "ZooKeeper max retries must be non-negative")
            private int maxRetries = 3;

            @Positive(message = "ZooKeeper max sleep time must be positive")
            private int maxSleepTime = 30000;

            // Getters and Setters
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

            public int getMaxSleepTime() {
                return maxSleepTime;
            }

            public void setMaxSleepTime(int maxSleepTime) {
                this.maxSleepTime = maxSleepTime;
            }
        }
    }
}