package com.example.distributedlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

/**
 * 應用程式配置屬性
 * 用於綁定應用程式相關的配置
 */
@ConfigurationProperties(prefix = "app")
@Validated
public class ApplicationProperties {

    @NotBlank(message = "Application name cannot be blank")
    private String name = "Distributed Lock Research";

    private String version = "1.0.0";

    private String description = "Research project for distributed lock implementations";

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

