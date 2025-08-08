package com.example.distributedlock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 配置屬性啟用器
 * 確保所有配置屬性類別都被正確載入和綁定
 */
@Configuration
@EnableConfigurationProperties({
    DistributedLockProperties.class,
    ApplicationProperties.class,
    DataInitializationProperties.class
})
public class ConfigurationPropertiesConfig {
    // 這個類別的目的是確保所有配置屬性類別都被 Spring Boot 正確識別和載入
}