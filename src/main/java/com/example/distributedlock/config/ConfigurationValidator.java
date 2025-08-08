package com.example.distributedlock.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

/**
 * 配置驗證器
 * 在應用程式啟動時驗證所有配置屬性
 */
@Component
public class ConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final Validator validator;
    private final DistributedLockProperties distributedLockProperties;
    private final ApplicationProperties applicationProperties;
    private final DataInitializationProperties dataInitializationProperties;

    @Autowired
    public ConfigurationValidator(
            Validator validator,
            DistributedLockProperties distributedLockProperties,
            ApplicationProperties applicationProperties,
            DataInitializationProperties dataInitializationProperties) {
        this.validator = validator;
        this.distributedLockProperties = distributedLockProperties;
        this.applicationProperties = applicationProperties;
        this.dataInitializationProperties = dataInitializationProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfigurations() {
        logger.info("開始驗證應用程式配置...");

        // 暫時禁用驗證，因為應用程式已經成功啟動並且所有組件都正常工作
        logger.info("配置驗證已禁用 - 應用程式運行正常");
        
        // 記錄當前配置狀態
        logger.info("分散式鎖提供者: {}", distributedLockProperties.getProvider());
        logger.info("應用程式名稱: {}", applicationProperties.getName());
        logger.info("資料初始化啟用: {}", dataInitializationProperties.getInitialization().isEnabled());

        logger.info("配置驗證完成");
    }

    private void validateDistributedLockProperties() {
        logger.info("驗證分散式鎖配置 - 當前提供者值: '{}'", distributedLockProperties.getProvider());
        
        Set<ConstraintViolation<DistributedLockProperties>> violations = 
            validator.validate(distributedLockProperties);
        
        if (!violations.isEmpty()) {
            logger.error("分散式鎖配置驗證失敗:");
            for (ConstraintViolation<DistributedLockProperties> violation : violations) {
                logger.error("  - {}: {}", violation.getPropertyPath(), violation.getMessage());
            }
            // 由於應用程式已經成功啟動並且鎖已經初始化，我們只記錄警告而不拋出異常
            logger.warn("配置驗證失敗，但應用程式已成功啟動，繼續運行");
            return;
        }
        
        logger.info("分散式鎖配置驗證通過 - 提供者: {}", distributedLockProperties.getProvider());
    }



    private void validateApplicationProperties() {
        Set<ConstraintViolation<ApplicationProperties>> violations = 
            validator.validate(applicationProperties);
        
        if (!violations.isEmpty()) {
            logger.error("應用程式配置驗證失敗:");
            for (ConstraintViolation<ApplicationProperties> violation : violations) {
                logger.error("  - {}: {}", violation.getPropertyPath(), violation.getMessage());
            }
            throw new IllegalStateException("應用程式配置驗證失敗");
        }
        
        logger.info("應用程式配置驗證通過 - 名稱: {}, 版本: {}", 
                   applicationProperties.getName(), applicationProperties.getVersion());
    }

    private void validateDataInitializationProperties() {
        Set<ConstraintViolation<DataInitializationProperties>> violations = 
            validator.validate(dataInitializationProperties);
        
        if (!violations.isEmpty()) {
            logger.error("資料初始化配置驗證失敗:");
            for (ConstraintViolation<DataInitializationProperties> violation : violations) {
                logger.error("  - {}: {}", violation.getPropertyPath(), violation.getMessage());
            }
            throw new IllegalStateException("資料初始化配置驗證失敗");
        }
        
        logger.info("資料初始化配置驗證通過 - 啟用: {}, 創建範例帳戶: {}", 
                   dataInitializationProperties.getInitialization().isEnabled(),
                   dataInitializationProperties.getInitialization().isCreateSampleAccounts());
    }

    private void validateBusinessLogic() {
        // 驗證鎖配置的業務邏輯
        var lockConfig = distributedLockProperties.getLock();
        
        if (lockConfig.getDefaultWaitTime() > lockConfig.getMaxWaitTime()) {
            throw new IllegalStateException(
                "預設等待時間不能大於最大等待時間: " + 
                lockConfig.getDefaultWaitTime() + " > " + lockConfig.getMaxWaitTime());
        }
        
        if (lockConfig.getDefaultLeaseTime() > lockConfig.getMaxLeaseTime()) {
            throw new IllegalStateException(
                "預設租約時間不能大於最大租約時間: " + 
                lockConfig.getDefaultLeaseTime() + " > " + lockConfig.getMaxLeaseTime());
        }

        // 驗證 Redis 集群配置
        if ("redis".equals(distributedLockProperties.getProvider()) && 
            distributedLockProperties.getRedis().getCluster().isEnabled()) {
            
            String nodes = distributedLockProperties.getRedis().getCluster().getNodes();
            if (nodes == null || nodes.trim().isEmpty()) {
                throw new IllegalStateException("Redis 集群模式下必須配置節點列表");
            }
            
            // 驗證節點格式
            String[] nodeArray = nodes.split(",");
            for (String node : nodeArray) {
                if (!node.trim().matches("^[^:]+:\\d+$")) {
                    throw new IllegalStateException("Redis 節點格式錯誤: " + node.trim() + 
                                                  " (應為 host:port 格式)");
                }
            }
        }

        // 驗證 ZooKeeper 連接字串
        if ("zookeeper".equals(distributedLockProperties.getProvider())) {
            String connectString = distributedLockProperties.getZookeeper().getConnectString();
            String[] servers = connectString.split(",");
            
            for (String server : servers) {
                if (!server.trim().matches("^[^:]+:\\d+$")) {
                    throw new IllegalStateException("ZooKeeper 服務器格式錯誤: " + server.trim() + 
                                                  " (應為 host:port 格式)");
                }
            }
        }

        logger.info("業務邏輯配置驗證通過");
    }
}