package com.example.distributedlock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置類別
 * 配置 Swagger UI 和 API 文件
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.name:Distributed Lock Research}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.description:Research project for distributed lock implementations}")
    private String appDescription;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Value("${swagger.server.url:}")
    private String customServerUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName)
                        .version(appVersion)
                        .description(appDescription + "\n\n" +
                                "本專案實作了基於 Redis 和 ZooKeeper 的分散式鎖機制，" +
                                "並提供銀行交易場景的示範應用程式。\n\n" +
                                "## 主要功能\n" +
                                "- **分散式鎖實作**: 支援 Redis 和 ZooKeeper 兩種分散式鎖提供者\n" +
                                "- **銀行交易模擬**: 提供匯款和扣款等交易操作\n" +
                                "- **併發控制**: 使用分散式鎖確保交易的原子性和一致性\n" +
                                "- **監控指標**: 提供詳細的業務和技術指標\n" +
                                "- **健康檢查**: 監控系統各組件的健康狀態\n\n" +
                                "## 使用說明\n" +
                                "1. 使用 `/api/accounts/{accountNumber}/balance` 查詢帳戶餘額\n" +
                                "2. 使用 `/api/transfer` 進行匯款操作\n" +
                                "3. 使用 `/api/withdraw` 進行扣款操作\n" +
                                "4. 使用 `/actuator` 端點監控系統狀態\n\n" +
                                "## 分散式鎖配置\n" +
                                "可以通過環境變數 `LOCK_PROVIDER` 切換鎖提供者：\n" +
                                "- `redis`: 使用 Redis 分散式鎖\n" +
                                "- `zookeeper`: 使用 ZooKeeper 分散式鎖")
                        .contact(new Contact()
                                .name("Distributed Lock Research Team")
                                .email("research@example.com")
                                .url("https://github.com/example/distributed-lock-research"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(getServerList())
                .tags(List.of(
                        new Tag()
                                .name("Banking Operations")
                                .description("銀行交易相關操作，包括匯款、扣款和餘額查詢"),
                        new Tag()
                                .name("Lock Management")
                                .description("分散式鎖管理相關操作"),
                        new Tag()
                                .name("System Monitoring")
                                .description("系統監控和健康檢查相關端點"),
                        new Tag()
                                .name("Configuration")
                                .description("系統配置和資訊查詢")
                ));
    }

    /**
     * 根據不同環境動態生成 Server URL 列表
     */
    private List<Server> getServerList() {
        // 如果有自定義 server URL，優先使用
        if (customServerUrl != null && !customServerUrl.trim().isEmpty()) {
            return List.of(
                    new Server()
                            .url(customServerUrl + contextPath)
                            .description("自定義環境")
            );
        }

        // 根據 active profile 決定 server URL
        if (activeProfile.contains("docker")) {
            // Docker 環境：外部訪問使用映射的端口
            return List.of(
                    new Server()
                            .url("http://localhost:8081" + contextPath)
                            .description("Docker 環境 - App1"),
                    new Server()
                            .url("http://localhost:8082" + contextPath)
                            .description("Docker 環境 - App2"),
                    new Server()
                            .url("http://localhost:8083" + contextPath)
                            .description("Docker 環境 - App3"),
                    new Server()
                            .url("http://localhost:8080" + contextPath)
                            .description("Docker 環境 - 負載均衡器")
            );
        } else if (activeProfile.contains("prod")) {
            // 生產環境
            return List.of(
                    new Server()
                            .url("https://api.example.com" + contextPath)
                            .description("生產環境")
            );
        } else {
            // 本地開發環境
            return List.of(
                    new Server()
                            .url("http://localhost:8080" + contextPath)
                            .description("本地開發環境")
            );
        }
    }
}