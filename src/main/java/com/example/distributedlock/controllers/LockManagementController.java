package com.example.distributedlock.controllers;

import com.example.distributedlock.config.LockConfiguration;
import com.example.distributedlock.factory.DistributedLockFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 鎖管理控制器
 * 提供分散式鎖提供者的動態切換和狀態查詢 API
 */
@RestController
@RequestMapping("/lock-management")
@Tag(name = "Lock Management", description = "分散式鎖管理相關操作")
public class LockManagementController {

    private static final Logger logger = LoggerFactory.getLogger(LockManagementController.class);

    private final LockConfiguration.LockManager lockManager;

    @Autowired
    public LockManagementController(LockConfiguration.LockManager lockManager) {
        this.lockManager = lockManager;
    }

    /**
     * 獲取當前鎖提供者狀態
     * 
     * @return 鎖提供者狀態資訊
     */
    @Operation(
        summary = "獲取鎖狀態",
        description = "獲取當前分散式鎖提供者的狀態資訊，包括當前提供者、活躍鎖數量、可用性等。",
        tags = {"Lock Management"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "獲取狀態成功",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "狀態資訊範例",
                    value = """
                    {
                      "success": true,
                      "currentProvider": "redis",
                      "activeLocks": 3,
                      "redisAvailable": true,
                      "zookeeperAvailable": true,
                      "supportedProviders": ["redis", "zookeeper"],
                      "statistics": {
                        "totalLockAcquisitions": 150,
                        "successfulAcquisitions": 148,
                        "failedAcquisitions": 2,
                        "averageAcquisitionTime": 25.5
                      }
                    }
                    """
                )
            )
        ),
        @ApiResponse(responseCode = "500", description = "獲取狀態失敗")
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLockStatus() {
        try {
            LockConfiguration.LockProviderStatus status = lockManager.getProviderStatus();
            DistributedLockFactory.LockStatistics stats = lockManager.getLockStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("currentProvider", status.getCurrentProvider());
            response.put("activeLocks", status.getActiveLocks());
            response.put("redisAvailable", status.isRedisAvailable());
            response.put("zookeeperAvailable", status.isZookeeperAvailable());
            response.put("supportedProviders", lockManager.getSupportedProviders());
            response.put("statistics", stats);

            logger.debug("獲取鎖狀態: {}", response);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("獲取鎖狀態失敗", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "獲取鎖狀態失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 切換鎖提供者
     * 
     * @param request 切換請求
     * @return 切換結果
     */
    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchLockProvider(@RequestBody SwitchProviderRequest request) {
        try {
            logger.info("收到切換鎖提供者請求: {}", request.getProvider());

            if (request.getProvider() == null || request.getProvider().trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "鎖提供者不能為空");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            String currentProvider = lockManager.getCurrentProvider();
            String newProvider = request.getProvider().toLowerCase().trim();

            if (currentProvider.equals(newProvider)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "鎖提供者未改變");
                response.put("currentProvider", currentProvider);
                return ResponseEntity.ok(response);
            }

            boolean success = lockManager.switchProvider(newProvider);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);

            if (success) {
                response.put("message", String.format("成功切換鎖提供者從 %s 到 %s", currentProvider, newProvider));
                response.put("previousProvider", currentProvider);
                response.put("currentProvider", newProvider);
                logger.info("成功切換鎖提供者從 {} 到 {}", currentProvider, newProvider);
            } else {
                response.put("error", String.format("切換鎖提供者失敗，從 %s 到 %s", currentProvider, newProvider));
                response.put("currentProvider", lockManager.getCurrentProvider());
                logger.error("切換鎖提供者失敗，從 {} 到 {}", currentProvider, newProvider);
            }

            return success ? ResponseEntity.ok(response) : ResponseEntity.internalServerError().body(response);

        } catch (Exception e) {
            logger.error("切換鎖提供者時發生異常", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "切換鎖提供者失敗: " + e.getMessage());
            errorResponse.put("currentProvider", lockManager.getCurrentProvider());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 檢查鎖提供者可用性
     * 
     * @param provider 鎖提供者名稱
     * @return 可用性檢查結果
     */
    @GetMapping("/check/{provider}")
    public ResponseEntity<Map<String, Object>> checkProviderAvailability(@PathVariable String provider) {
        try {
            boolean available = lockManager.isProviderAvailable(provider);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("provider", provider);
            response.put("available", available);

            logger.debug("檢查鎖提供者 {} 可用性: {}", provider, available);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("檢查鎖提供者可用性失敗: {}", provider, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("provider", provider);
            errorResponse.put("error", "檢查可用性失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 獲取支持的鎖提供者列表
     * 
     * @return 支持的鎖提供者列表
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getSupportedProviders() {
        try {
            String[] providers = lockManager.getSupportedProviders();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("supportedProviders", providers);
            response.put("currentProvider", lockManager.getCurrentProvider());

            // 檢查每個提供者的可用性
            Map<String, Boolean> availability = new HashMap<>();
            for (String provider : providers) {
                availability.put(provider, lockManager.isProviderAvailable(provider));
            }
            response.put("availability", availability);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("獲取支持的鎖提供者列表失敗", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "獲取提供者列表失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 切換提供者請求類別
     */
    public static class SwitchProviderRequest {
        private String provider;

        public SwitchProviderRequest() {}

        public SwitchProviderRequest(String provider) {
            this.provider = provider;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        @Override
        public String toString() {
            return String.format("SwitchProviderRequest{provider='%s'}", provider);
        }
    }
}