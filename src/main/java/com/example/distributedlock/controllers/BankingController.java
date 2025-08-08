package com.example.distributedlock.controllers;

import com.example.distributedlock.dto.*;
import com.example.distributedlock.services.BankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 銀行業務控制器
 * 提供 REST API 端點，支援匯款、扣款和帳戶查詢功能
 */
@RestController
@RequestMapping("")
@Tag(name = "Banking Operations", description = "銀行交易相關操作，包括匯款、扣款和餘額查詢")
public class BankingController {
    
    private static final Logger logger = LoggerFactory.getLogger(BankingController.class);
    
    private final BankingService bankingService;
    
    @Autowired
    public BankingController(BankingService bankingService) {
        this.bankingService = bankingService;
    }
    
    /**
     * 匯款 API 端點
     * 
     * @param request 匯款請求
     * @return 匯款結果
     */
    @Operation(
        summary = "執行匯款操作",
        description = "在兩個帳戶之間進行資金轉移。此操作使用分散式鎖確保交易的原子性和一致性。",
        tags = {"Banking Operations"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "匯款成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.example.distributedlock.dto.ApiResponse.class),
                examples = @ExampleObject(
                    name = "成功範例",
                    value = """
                    {
                      "success": true,
                      "message": "匯款成功",
                      "data": {
                        "transactionId": "TXN123456",
                        "success": true,
                        "message": "匯款完成",
                        "fromAccount": "ACC001",
                        "toAccount": "ACC002",
                        "amount": 1000.00,
                        "lockProvider": "redis"
                      },
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "匯款失敗 - 業務邏輯錯誤",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "餘額不足範例",
                    value = """
                    {
                      "success": false,
                      "message": "餘額不足",
                      "data": null,
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "系統內部錯誤",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "系統錯誤範例",
                    value = """
                    {
                      "success": false,
                      "message": "系統內部錯誤",
                      "data": null,
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        )
    })
    @PostMapping("/transfer")
    public ResponseEntity<com.example.distributedlock.dto.ApiResponse<TransactionResult>> transfer(
        @Parameter(
            description = "匯款請求資料",
            required = true,
            content = @Content(
                examples = @ExampleObject(
                    name = "匯款請求範例",
                    value = """
                    {
                      "fromAccount": "ACC001",
                      "toAccount": "ACC002",
                      "amount": 1000.00
                    }
                    """
                )
            )
        )
        @Valid @RequestBody TransferRequest request) {
        logger.info("收到匯款請求: {}", request);
        
        try {
            TransactionResult result = bankingService.transfer(
                request.getFromAccount(), 
                request.getToAccount(), 
                request.getAmount()
            );
            
            if (result.isSuccess()) {
                logger.info("匯款成功: 交易ID={}", result.getTransactionId());
                return ResponseEntity.ok(com.example.distributedlock.dto.ApiResponse.success("匯款成功", result));
            } else {
                logger.warn("匯款失敗: {}", result.getMessage());
                return ResponseEntity.badRequest()
                    .body(com.example.distributedlock.dto.ApiResponse.failure(result.getMessage(), result));
            }
        } catch (Exception e) {
            logger.error("匯款處理異常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(com.example.distributedlock.dto.ApiResponse.failure("系統內部錯誤"));
        }
    }
    
    /**
     * 扣款 API 端點
     * 
     * @param request 扣款請求
     * @return 扣款結果
     */
    @Operation(
        summary = "執行扣款操作",
        description = "從指定帳戶扣除指定金額。此操作使用分散式鎖確保帳戶餘額的一致性。",
        tags = {"Banking Operations"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "扣款成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.example.distributedlock.dto.ApiResponse.class),
                examples = @ExampleObject(
                    name = "成功範例",
                    value = """
                    {
                      "success": true,
                      "message": "扣款成功",
                      "data": {
                        "transactionId": "TXN123457",
                        "success": true,
                        "message": "扣款完成",
                        "fromAccount": "ACC001",
                        "toAccount": null,
                        "amount": 500.00,
                        "lockProvider": "zookeeper"
                      },
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "扣款失敗 - 業務邏輯錯誤",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "餘額不足範例",
                    value = """
                    {
                      "success": false,
                      "message": "餘額不足",
                      "data": null,
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "系統內部錯誤"
        )
    })
    @PostMapping("/withdraw")
    public ResponseEntity<com.example.distributedlock.dto.ApiResponse<TransactionResult>> withdraw(
        @Parameter(
            description = "扣款請求資料",
            required = true,
            content = @Content(
                examples = @ExampleObject(
                    name = "扣款請求範例",
                    value = """
                    {
                      "accountNumber": "ACC001",
                      "amount": 500.00
                    }
                    """
                )
            )
        )
        @Valid @RequestBody WithdrawalRequest request) {
        logger.info("收到扣款請求: {}", request);
        
        try {
            TransactionResult result = bankingService.withdraw(
                request.getAccountNumber(), 
                request.getAmount()
            );
            
            if (result.isSuccess()) {
                logger.info("扣款成功: 交易ID={}", result.getTransactionId());
                return ResponseEntity.ok(com.example.distributedlock.dto.ApiResponse.success("扣款成功", result));
            } else {
                logger.warn("扣款失敗: {}", result.getMessage());
                return ResponseEntity.badRequest()
                    .body(com.example.distributedlock.dto.ApiResponse.failure(result.getMessage(), result));
            }
        } catch (Exception e) {
            logger.error("扣款處理異常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(com.example.distributedlock.dto.ApiResponse.failure("系統內部錯誤"));
        }
    }
    
    /**
     * 查詢帳戶餘額 API 端點
     * 
     * @param accountNumber 帳戶號碼
     * @return 帳戶餘額資訊
     */
    @Operation(
        summary = "查詢帳戶餘額",
        description = "根據帳戶號碼查詢帳戶的當前餘額和狀態資訊。",
        tags = {"Banking Operations"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "查詢成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = com.example.distributedlock.dto.ApiResponse.class),
                examples = @ExampleObject(
                    name = "成功範例",
                    value = """
                    {
                      "success": true,
                      "message": "查詢成功",
                      "data": {
                        "accountNumber": "ACC001",
                        "balance": 9500.00,
                        "status": "ACTIVE"
                      },
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "帳戶不存在",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "帳戶不存在範例",
                    value = """
                    {
                      "success": false,
                      "message": "帳戶不存在",
                      "data": null,
                      "timestamp": "2024-01-01T12:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "系統內部錯誤"
        )
    })
    @GetMapping("/accounts/{accountNumber}/balance")
    public ResponseEntity<com.example.distributedlock.dto.ApiResponse<AccountBalance>> getBalance(
        @Parameter(
            description = "帳戶號碼",
            required = true,
            example = "ACC001"
        )
        @PathVariable String accountNumber) {
        logger.info("查詢帳戶餘額: {}", accountNumber);
        
        try {
            AccountBalance balance = bankingService.getBalance(accountNumber);
            
            if (balance != null) {
                logger.info("查詢餘額成功: 帳戶={}, 餘額={}", accountNumber, balance.getBalance());
                return ResponseEntity.ok(com.example.distributedlock.dto.ApiResponse.success("查詢成功", balance));
            } else {
                logger.warn("帳戶不存在: {}", accountNumber);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(com.example.distributedlock.dto.ApiResponse.failure("帳戶不存在"));
            }
        } catch (Exception e) {
            logger.error("查詢餘額異常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(com.example.distributedlock.dto.ApiResponse.failure("系統內部錯誤"));
        }
    }
}